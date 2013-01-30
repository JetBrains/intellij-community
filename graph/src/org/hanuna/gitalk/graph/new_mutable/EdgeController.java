package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.common.HashInvertibleMap;
import org.hanuna.gitalk.common.InvertibleMap;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author erokhins
 */
public class EdgeController {
    private final InvertibleMap<Edge, Node> upNodes = new HashInvertibleMap<Edge, Node>();
    private final InvertibleMap<Edge, Node> downNodes = new HashInvertibleMap<Edge, Node>();

    public void addEdge(@NotNull Node upNode, @NotNull Node downNode, @NotNull Edge edge) {
        upNodes.put(edge, upNode);
        downNodes.put(edge, downNode);
    }

    public void removeEdge(@NotNull Edge edge) {
        upNodes.remove(edge);
        downNodes.remove(edge);
    }

    @NotNull
    public Node getUpNode(@NotNull Edge edge) {
        Node node = upNodes.get(edge);
        if (node == null) {
            throw new IllegalStateException("request up node of edge, but edge doesn't add");
        }
        return node;
    }

    @NotNull
    public Node getDownNode(@NotNull Edge edge) {
        Node node = downNodes.get(edge);
        if (node == null) {
            throw new IllegalStateException("request down node of edge, but edge doesn't add");
        }
        return node;
    }

    @NotNull
    public Set<Edge> getUpEdges(@NotNull Node node) {
        return downNodes.getKeys(node);
    }

    @NotNull
    public Set<Edge> getDownEdges(@NotNull Node node) {
        return upNodes.getKeys(node);
    }
}
