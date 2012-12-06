package org.hanuna.gitalk.printmodel.cells;

import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public final class EdgeCell implements Cell {
    private final Edge edge;

    public EdgeCell(@NotNull Edge edge) {
        this.edge = edge;

    }

    @NotNull
    public Edge getEdge() {
        return edge;
    }
}
