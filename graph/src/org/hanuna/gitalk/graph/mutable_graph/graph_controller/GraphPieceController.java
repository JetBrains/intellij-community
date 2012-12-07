package org.hanuna.gitalk.graph.mutable_graph.graph_controller;

import org.hanuna.gitalk.graph.GraphPiece;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
public interface GraphPieceController {

    @Nullable
    public GraphPiece relatePiece(@NotNull GraphElement graphElement);
}
