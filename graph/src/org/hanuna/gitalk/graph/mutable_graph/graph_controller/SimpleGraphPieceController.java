package org.hanuna.gitalk.graph.mutable_graph.graph_controller;

import org.hanuna.gitalk.graph.GraphPiece;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.mutable_graph.MutableGraph;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class SimpleGraphPieceController implements GraphPieceController {
    private final MutableGraph graph;

    public SimpleGraphPieceController(MutableGraph graph) {
        this.graph = graph;
    }

    @Override
    public GraphPiece relatePiece(@NotNull GraphElement graphElement) {
        return null;
    }
}
