package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.GraphFragmentController;
import org.hanuna.gitalk.graph.mutable_graph.GraphBuilder;
import org.hanuna.gitalk.printmodel.GraphPrintCellModel;
import org.hanuna.gitalk.printmodel.SelectController;
import org.hanuna.gitalk.printmodel.impl.GraphPrintCellModelImpl;
import org.hanuna.gitalk.refs.RefsModel;
import org.jetbrains.annotations.NotNull;

import java.util.*;


/**
 * @author erokhins
 */
public class DataPack {
    private final RefsModel refsModel;
    private final List<Commit> commits;
    private Graph graph;
    private GraphPrintCellModel printCellModel;

    public DataPack(RefsModel refsModel, List<Commit> commits) {
        this.refsModel = refsModel;
        this.commits = commits;
        MyTimer graphTimer = new MyTimer("graph build");
        graph = GraphBuilder.build(commits);
        graphTimer.print();

        MyTimer printModelTimer = new MyTimer("print model build");
        printCellModel = new GraphPrintCellModelImpl(graph);
        printModelTimer.print();
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
        printCellModel = new GraphPrintCellModelImpl(graph);
    }

    @NotNull
    public RefsModel getRefsModel() {
        return refsModel;
    }

    @NotNull
    public Graph getGraph() {
        return graph;
    }

    @NotNull
    public GraphPrintCellModel getPrintCellModel() {
        return printCellModel;
    }

    @NotNull
    public GraphFragmentController getFragmentController() {
        return graph.getFragmentController();
    }

    @NotNull
    public SelectController getSelectController() {
        return printCellModel.getSelectController();
    }
}
