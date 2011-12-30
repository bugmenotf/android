package com.github.mobile.android.issue;

import static com.github.mobile.android.issue.ViewIssueActivity.viewIssueIntentFor;
import static com.github.mobile.android.util.GitHubIntents.EXTRA_REPOSITORY;
import static com.madgag.android.listviews.ViewInflator.viewInflatorFor;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.github.mobile.android.AccountDataManager;
import com.github.mobile.android.R;
import com.github.mobile.android.R.id;
import com.github.mobile.android.R.layout;
import com.github.mobile.android.RequestFuture;
import com.github.mobile.android.util.Avatar;
import com.github.mobile.android.util.GitHubIntents;
import com.github.mobile.android.util.GitHubIntents.Builder;
import com.google.inject.Inject;
import com.madgag.android.listviews.ViewHoldingListAdapter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.Repository;

import roboguice.activity.RoboFragmentActivity;
import roboguice.inject.InjectExtra;
import roboguice.inject.InjectView;

/**
 * Activity for browsing a list of issues
 */
public class IssueBrowseActivity extends RoboFragmentActivity {

    private static final int CODE_FILTER = 1;

    /**
     * Create intent to browse a repository's issues
     *
     * @param repository
     * @return intent
     */
    public static Intent createIntent(Repository repository) {
        return new Builder("repo.issues.VIEW").repo(repository).toIntent();
    }

    @InjectView(android.R.id.list)
    private ListView issueList;

    @Inject
    private AccountDataManager cache;

    @InjectExtra(EXTRA_REPOSITORY)
    private Repository repo;

    private IssueFilter filter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.repo_issue_list);

        ((TextView) findViewById(id.tv_repo_name)).setText(repo.getName());
        ((TextView) findViewById(id.tv_owner_name)).setText(repo.getOwner().getLogin() + " /");
        Avatar.bind(this, (ImageView) findViewById(id.iv_gravatar), repo.getOwner());

        if (savedInstanceState != null)
            filter = (IssueFilter) savedInstanceState.getSerializable(GitHubIntents.EXTRA_ISSUE_FILTER);

        if (filter == null)
            filter = new IssueFilter();

        issueList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> list, View view, int position, long id) {
                Issue issue = (Issue) list.getItemAtPosition(position);
                startActivity(viewIssueIntentFor(issue));
            }
        });

        updateFilterSummary();
        loadIssues(repo);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.issues, menu);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(GitHubIntents.EXTRA_ISSUE_FILTER, filter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.create_issue:
            return true;
        case R.id.filter_issues:
            startActivityForResult(FilterIssuesActivity.createIntent(repo, filter), CODE_FILTER);
            return true;
        case R.id.bookmark_filter:
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void updateFilterSummary() {
        CharSequence display = filter.toDisplay();
        TextView summary = (TextView) findViewById(id.tv_filter_summary);
        if (display.length() > 0) {
            summary.setText(display);
            summary.setVisibility(View.VISIBLE);
        } else
            summary.setVisibility(View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == CODE_FILTER && data != null) {
            Repository repo = (Repository) getIntent().getSerializableExtra(EXTRA_REPOSITORY);
            filter = (IssueFilter) data.getSerializableExtra(GitHubIntents.EXTRA_ISSUE_FILTER);
            updateFilterSummary();
            loadIssues(repo);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void loadIssues(final Repository repo) {
        final Set<Issue> all = new TreeSet<Issue>(new CreatedAtComparator());
        final Iterator<Map<String, String>> filters = filter.iterator();
        final RequestFuture<List<Issue>> callback = new RequestFuture<List<Issue>>() {

            public void success(List<Issue> issues) {
                all.addAll(issues);
                if (filters.hasNext())
                    cache.getIssues(repo, filters.next(), this);
                else
                    issueList.setAdapter(new ViewHoldingListAdapter<Issue>(new ArrayList<Issue>(all), viewInflatorFor(
                            IssueBrowseActivity.this, layout.repo_issue_list_item), RepoIssueViewHolder.FACTORY));
            }
        };
        cache.getIssues(repo, filters.next(), callback);
    }
}