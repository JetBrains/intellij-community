package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
public interface Graph {

    @NotNull
    public ReadOnlyList<NodeRow> getNodeRows();

    @Nullable
    public GraphPiece relatePiece(@NotNull GraphElement graphElement);
}
