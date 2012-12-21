package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.graph.graph_elements.NodeRow;
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
