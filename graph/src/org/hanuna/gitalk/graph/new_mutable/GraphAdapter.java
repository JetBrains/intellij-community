package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.GraphFragmentController;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.hanuna.gitalk.graph.new_mutable.fragments.FragmentManager;
import org.hanuna.gitalk.graph.new_mutable.fragments.GraphFragmentControllerAdapter;
import org.hanuna.gitalk.graph.new_mutable.fragments.UnhiddenNodeFunction;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author erokhins
 */
public class GraphAdapter implements Graph {
    private final MutableGraph graph;
    private final List<NodeRow> nodeRows;
    private final GraphFragmentController graphFragmentController;
    private final FragmentManager fragmentManager;
    private final Set<Commit> unhiddenCommits;

    public GraphAdapter(@NotNull MutableGraph graph, List<Commit> unhiddenCommits) {
        this.graph = graph;
        nodeRows = graph.getNodeRows();
        fragmentManager = new FragmentManager(graph);
        fragmentManager.setUnhiddenNodes(new UnhiddenNodeFunction() {
            @Override
            public boolean isUnhiddenNode(@NotNull Node node) {
                return GraphAdapter.this.unhiddenCommits.contains(node.getCommit());
            }
        });
        graphFragmentController = new GraphFragmentControllerAdapter(fragmentManager);
        this.unhiddenCommits = new HashSet<Commit>(unhiddenCommits);
    }

    @NotNull
    @Override
    public List<NodeRow> getNodeRows() {
        return nodeRows;
    }

    @NotNull
    @Override
    public GraphFragmentController getFragmentController() {
        return graphFragmentController;
    }

    @Override
    public void addUpdateListener(@NotNull final GraphUpdateListener updateListener) {
        graph.addUpdateListener(new Executor<Replace>() {
            @Override
            public void execute(Replace key) {
                updateListener.doReplace(key);
            }
        });
    }

    @Override
    public void removeAllListeners() {
        graph.removeAllListeners();
    }

    public FragmentManager getFragmentManager() {
        return fragmentManager;
    }
}
