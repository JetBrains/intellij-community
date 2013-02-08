package org.hanuna.gitalk.graphmodel.fragment;

import org.hanuna.gitalk.common.MultiMap;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.mutable.GraphDecorator;
import org.hanuna.gitalk.graphmodel.GraphFragment;
import org.hanuna.gitalk.graphmodel.fragment.elements.HideFragmentEdge;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author erokhins
 */
public class FragmentGraphDecorator implements GraphDecorator {
    private final Set<Node> hideNodes = new HashSet<Node>();
    private final MultiMap<Node, Edge> upNodeEdges = new MultiMap<Node, Edge>();
    private final MultiMap<Node, Edge> downNodeEdges = new MultiMap<Node, Edge>();

    @Override
    public boolean isVisibleNode(@NotNull Node node) {
        return !hideNodes.contains(node);
    }

    @Override
    public Edge getHideFragmentUpEdge(@NotNull Node node) {
        List<Edge> edges = downNodeEdges.get(node);
        for (Edge edge : edges) {
            if (isVisibleNode(edge.getUpNode())) {
                return edge;
            }
        }
        return null;
    }

    @Override
    public Edge getHideFragmentDownEdge(@NotNull Node node) {
        List<Edge> edges = upNodeEdges.get(node);
        for (Edge edge : edges) {
            if (isVisibleNode(edge.getDownNode())) {
                return edge;
            }
        }
        return null;
    }

    public void show(@NotNull GraphFragment fragment, @NotNull Edge hideFragmentEdge) {
        upNodeEdges.remove(hideFragmentEdge.getUpNode(), hideFragmentEdge);
        downNodeEdges.remove(hideFragmentEdge.getDownNode(), hideFragmentEdge);
        hideNodes.removeAll(fragment.getIntermediateNodes());
    }

    public Edge hide(@NotNull GraphFragment fragment) {
        Edge edge = new HideFragmentEdge(fragment.getUpNode(), fragment.getDownNode());
        upNodeEdges.put(edge.getUpNode(), edge);
        downNodeEdges.put(edge.getDownNode(), edge);
        hideNodes.addAll(fragment.getIntermediateNodes());
        return edge;
    }

}
