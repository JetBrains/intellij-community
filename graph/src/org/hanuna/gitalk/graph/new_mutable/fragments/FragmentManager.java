package org.hanuna.gitalk.graph.new_mutable.fragments;

import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.new_mutable.MutableGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author erokhins
 */
public class FragmentManager {
    private final MutableGraph graph;
    private final FragmentGenerator fragmentGenerator;
    private final Map<Edge, NewGraphFragment> hideFragments = new HashMap<Edge, NewGraphFragment>();

    public FragmentManager(MutableGraph graph) {
        this.graph = graph;
        fragmentGenerator = new FragmentGenerator(graph);
    }

    @Nullable
    public NewGraphFragment relateFragment(@NotNull GraphElement graphElement) {
        Node node = graphElement.getNode();
        if (node != null) {
            NewGraphFragment fragment = fragmentGenerator.getShortFragment(node);
            if (fragment != null && !fragment.getIntermediateNodes().isEmpty()){
                return fragment;
            }
        } else {
            Edge edge = graphElement.getEdge();
            if (edge != null && edge.getType() == Edge.Type.HIDE_FRAGMENT) {
                return hideFragments.get(edge);
            }
        }
        return null;
    }

    @NotNull
    private Edge getHideFragmentEdge(@NotNull NewGraphFragment fragment) {
        if (fragment.isVisible()) {
            throw new IllegalArgumentException("is not hide fragment: " + fragment);
        }
        List<Edge> downEdges = fragment.getUpNode().getDownEdges();
        if (downEdges.size() == 1)  {
            Edge edge = downEdges.get(0);
            if (edge.getType() == Edge.Type.HIDE_FRAGMENT && edge.getDownNode() == fragment.getDownNode()) {
                return edge;
            }
        }
        throw new IllegalArgumentException("is bad hide fragment: " + fragment);
    }

    public Replace show(@NotNull NewGraphFragment fragment) {
        if (fragment.isVisible()) {
            throw new IllegalArgumentException("is not hide fragment: " + fragment);
        }
        graph.getEdgeController().removeEdge(getHideFragmentEdge(fragment));
        graph.getVisibilityController().show(fragment.getIntermediateNodes());
        fragment.setVisibility(true);
        return graph.intermediateUpdate(fragment.getUpNode(), fragment.getDownNode());
    }

    public Replace hide(@NotNull NewGraphFragment fragment) {
        if (!fragment.isVisible()) {
            throw new IllegalArgumentException("is hide fragment: " + fragment);
        }
        Edge edge = graph.getEdgeController().createEdge(fragment.getUpNode(), fragment.getDownNode(),
                fragment.getUpNode().getBranch(), Edge.Type.HIDE_FRAGMENT);
        hideFragments.put(edge, fragment);
        graph.getVisibilityController().hide(fragment.getIntermediateNodes());
        fragment.setVisibility(false);
        return graph.intermediateUpdate(fragment.getUpNode(), fragment.getDownNode());
    }


}
