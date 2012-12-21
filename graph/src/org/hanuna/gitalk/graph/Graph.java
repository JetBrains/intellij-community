package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.graph.graph_elements.NodeRow;
import org.hanuna.gitalk.graph.mutable_graph.graph_fragment_controller.GraphFragmentController;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface Graph {

    @NotNull
    public List<NodeRow> getNodeRows();

    @NotNull
    public GraphFragmentController getFragmentController();
}
