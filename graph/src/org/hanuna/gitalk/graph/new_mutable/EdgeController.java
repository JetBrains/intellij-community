package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.graph.elements.Branch;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.new_mutable.elements.MutableNode;
import org.hanuna.gitalk.graph.new_mutable.elements.SimpleEdge;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public class EdgeController {



    public Edge createEdge(@NotNull Node upNode, @NotNull Node downNode,
                           @NotNull Branch edgeBranch, @NotNull Edge.Type type) {
        Edge edge = new SimpleEdge(upNode, downNode, type, edgeBranch);
        ((MutableNode) upNode).getInnerDownEdges().add(edge);
        ((MutableNode) downNode).getInnerUpEdges().add(edge);
        return edge;
    }


    public void removeEdge(@NotNull Edge edge) {
        ((MutableNode) edge.getUpNode()).getInnerDownEdges().remove(edge);
        ((MutableNode) edge.getDownNode()).getInnerUpEdges().remove(edge);
    }

    public List<Edge> getAllDownEdges(@NotNull Node node) {
        return Collections.unmodifiableList(((MutableNode) node).getInnerDownEdges());
    }

}
