package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.graph_elements.NodeRow;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface Graph {

    @NotNull
    public ReadOnlyList<NodeRow> getNodeRows();

}
