package org.hanuna.gitalk.graph.mutable_graph.graph_elements_impl;

import org.hanuna.gitalk.graph.graph_elements.Branch;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class SimpleEdge implements Edge {
    private final Type type;
    private final Node up;
    private final Node down;
    private final Branch branch;

    public SimpleEdge(@NotNull Node up, @NotNull Node down, @NotNull Type type, @NotNull Branch branch) {
        this.up = up;
        this.down = down;
        this.type = type;
        this.branch = branch;
    }

    @NotNull
    @Override
    public Node getUpNode() {
        return up;
    }

    @NotNull
    @Override
    public Node getDownNode() {
        return down;
    }

    @NotNull
    @Override
    public Type getType() {
        return type;
    }

    @NotNull
    @Override
    public Branch getBranch() {
        return branch;
    }

    @Override
    public Node getNode() {
        return null;
    }

    @Override
    public Edge getEdge() {
        return this;
    }
}
