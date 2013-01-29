package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author erokhins
 */
public class EdgeController {

    public void addEdge(@NotNull Node upNode, @NotNull Node downNode, @NotNull Edge edge) {

    }

    public void removeEdge(@NotNull Node upNode, @NotNull Node downNode, @NotNull Edge edge) {

    }

    @NotNull
    public Node getUpNode(@NotNull Edge edge) {
        return null;
    }

    @NotNull
    public Node getDownNode(@NotNull Edge edge) {
        return null;
    }

    @NotNull
    public Set<Edge> getUpEdges(@NotNull Node node) {
        return null;
    }

    @NotNull
    public Set<Edge> getDownEdges(@NotNull Node node) {
        return null;
    }
}
