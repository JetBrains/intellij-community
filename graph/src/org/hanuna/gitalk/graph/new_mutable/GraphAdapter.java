package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.GraphFragmentController;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.hanuna.gitalk.graph.new_mutable.fragments.FragmentManager;
import org.hanuna.gitalk.graph.new_mutable.fragments.GraphFragmentControllerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class GraphAdapter implements Graph {
    private final MutableGraph graph;
    private final List<NodeRow> nodeRows;
    private final GraphFragmentController graphFragmentController;

    public GraphAdapter(MutableGraph graph) {
        this.graph = graph;
        nodeRows = graph.getNodeRows();
        FragmentManager fragmentManager = new FragmentManager(graph);
        graphFragmentController = new GraphFragmentControllerAdapter(fragmentManager);
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


}
