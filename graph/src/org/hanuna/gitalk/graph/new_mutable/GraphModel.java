package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.GraphFragmentController;
import org.hanuna.gitalk.graph.elements.Node;
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
public class GraphModel {
    private final MutableGraph graph;
    private final GraphFragmentController graphFragmentController;
    private final FragmentManager fragmentManager;
    private final Set<Commit> unhiddenCommits;

    public GraphModel(@NotNull MutableGraph graph, List<Commit> unhiddenCommits) {
        this.graph = graph;
        fragmentManager = new FragmentManager(graph);
        fragmentManager.setUnhiddenNodes(new UnhiddenNodeFunction() {
            @Override
            public boolean isUnhiddenNode(@NotNull Node node) {
                return GraphModel.this.unhiddenCommits.contains(node.getCommit());
            }
        });
        graphFragmentController = new GraphFragmentControllerAdapter(fragmentManager);
        this.unhiddenCommits = new HashSet<Commit>(unhiddenCommits);
    }

    public MutableGraph getGraph() {
        return graph;
    }

    public void addUpdateListener(@NotNull Executor<Replace> listener) {
        graph.addUpdateListener(listener);
    }

    public void removeAllListeners() {
        graph.removeAllListeners();
    }

    @NotNull
    public GraphFragmentController getFragmentController() {
        return graphFragmentController;
    }


    public FragmentManager getFragmentManager() {
        return fragmentManager;
    }
}
