package org.hanuna.gitalk.graph.new_mutable.fragments;

import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
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
        return null;
    }

    @NotNull
    private Edge getHideFragmentEdge(@NotNull NewGraphFragment fragment) {
        if (!fragment.isHideFragment()) {
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

    public void show(@NotNull NewGraphFragment fragment) {
        if (!fragment.isHideFragment()) {
            throw new IllegalArgumentException("is not hide fragment: " + fragment);
        }
        graph.getEdgeController().removeEdge(getHideFragmentEdge(fragment));
        graph.getVisibilityController().show(fragment.getIntermediateNodes());
        graph.intermediateUpdate(fragment.getUpNode(), fragment.getDownNode());
    }

    public void hide(@NotNull NewGraphFragment fragment) {
        if (fragment.isHideFragment()) {
            throw new IllegalArgumentException("is hide fragment: " + fragment);
        }
        Edge edge = graph.getEdgeController().createEdge(fragment.getUpNode(), fragment.getDownNode(),
                fragment.getUpNode().getBranch(), Edge.Type.HIDE_FRAGMENT);
        hideFragments.put(edge, fragment);
        graph.getVisibilityController().hide(fragment.getIntermediateNodes());
        graph.intermediateUpdate(fragment.getUpNode(), fragment.getDownNode());
    }


}
