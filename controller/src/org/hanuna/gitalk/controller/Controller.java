package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.mutable_graph.GraphBuilder;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefsModel;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

/**
 * @author erokhins
 */
public class Controller {
    private List<Commit> commits;
    private List<Ref> allRefs;
    private RefsModel refsModel;
    private Graph graph;

    public void prepare() throws IOException {
        commits = GitLogReader.readAllCommits();
        allRefs = GitLogReader.readAllRefs();
        refsModel = RefsModel.existedCommitRefs(allRefs, commits);
        graph = GraphBuilder.build(commits);
    }

    public RefsModel getRefsModel() {
        return refsModel;
    }

    public Graph getGraph() {
        return graph;
    }

    public void setShowBranches(@NotNull Set<Commit> startedCommit) {
        Set<Commit> notAddedVisibleCommits = new HashSet<Commit>();
        List<Commit> showCommits = new ArrayList<Commit>();

        for (Commit commit : commits) {
            if (startedCommit.contains(commit) || notAddedVisibleCommits.contains(commit)) {
                showCommits.add(commit);
                notAddedVisibleCommits.remove(commit);
                CommitData data = commit.getData();
                if (data != null) {
                    for (Commit parent : data.getParents()) {
                        notAddedVisibleCommits.add(parent);
                    }
                }
            }
        }
        graph = GraphBuilder.build(Collections.unmodifiableList(showCommits));
    }

}
