package org.hanuna.gitalk.graph.new_mutable.elements;

import org.hanuna.gitalk.graph.elements.Branch;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.new_mutable.MutableGraph;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class SimpleEdge implements Edge {
    private final MutableGraph graph;
    private final Type type;
    private final Branch branch;

    public SimpleEdge(MutableGraph graph, Type type, Branch branch) {
        this.graph = graph;
        this.type = type;
        this.branch = branch;
    }

    @NotNull
    @Override
    public Node getUpNode() {
        return graph.getEdgeController().getUpNode(this);
    }

    @NotNull
    @Override
    public Node getDownNode() {
        return graph.getEdgeController().getDownNode(this);
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
