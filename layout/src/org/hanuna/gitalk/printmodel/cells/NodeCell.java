package org.hanuna.gitalk.printmodel.cells;

import org.hanuna.gitalk.graph.graph_elements.Node;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public final class NodeCell implements Cell {
    private final Node node;

    public NodeCell(@NotNull Node node) {
        this.node = node;
    }

    @NotNull
    public Node getNode() {
        return node;
    }
}
