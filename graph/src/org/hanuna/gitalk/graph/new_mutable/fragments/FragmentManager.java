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

    public void setUnhiddenNodes(UnhiddenNodeFunction unhiddenNodes) {
        fragmentGenerator.setUnhiddenNodes(unhiddenNodes);
    }

    @Nullable
    public NewGraphFragment getFragment(@NotNull Node node) {
        graph.getVisibilityController().setShowAllBranchesFlag(true);
        NewGraphFragment fragment = fragmentGenerator.getFragment(node);
        graph.getVisibilityController().setShowAllBranchesFlag(false);
        return fragment;
    }


    @Nullable
    public Edge getHideEdge(@NotNull Node node) {
        for (Edge edge : node.getDownEdges()) {
            if (edge.getType() == Edge.Type.HIDE_FRAGMENT) {
                return edge;
            }
        }
        for (Edge edge : node.getUpEdges()) {
            if (edge.getType() == Edge.Type.HIDE_FRAGMENT) {
                return edge;
            }
        }
        return null;
    }

    @Nullable
    public NewGraphFragment relateFragment(@NotNull GraphElement graphElement) {
        Node node = graphElement.getNode();
        if (node != null) {
            Edge edge = getHideEdge(node);
            if (edge != null) {
                NewGraphFragment fragment = hideFragments.get(edge);
                assert fragment != null;
                return fragment;
            } else {
                NewGraphFragment fragment = getFragment(node);
                if (fragment != null && fragment.getDownNode().getRowIndex() >= node.getRowIndex()) {
                    return fragment;
                } else {
                    return null;
                }
            }
        } else {
            Edge edge = graphElement.getEdge();
            if (edge.getType() == Edge.Type.HIDE_FRAGMENT) {
                NewGraphFragment fragment = hideFragments.get(edge);
                assert fragment != null;
                return fragment;
            } else {
                NewGraphFragment fragment = getFragment(edge.getUpNode());
                if (fragment != null && fragment.getDownNode().getRowIndex() >= edge.getDownNode().getRowIndex()) {
                    return fragment;
                } else {
                    return null;
                }
            }
        }
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

    @Nullable
    public Edge getUpDownEdge(@NotNull NewGraphFragment fragment) {
        for (Edge edge : graph.getEdgeController().getAllDownEdges(fragment.getUpNode())) {
            if (edge.getDownNode() == fragment.getDownNode()) {
                return edge;
            }
        }
        return null;
    }

    public Replace show(@NotNull NewGraphFragment fragment) {
        if (fragment.isVisible()) {
            throw new IllegalArgumentException("is not hide fragment: " + fragment);
        }
        graph.getEdgeController().removeEdge(getHideFragmentEdge(fragment));
        graph.getVisibilityController().show(fragment.getIntermediateNodes());
        Edge upDownEdge = getUpDownEdge(fragment);
        if (upDownEdge != null){
            graph.getVisibilityController().showEdge(upDownEdge);
        }
        fragment.setVisibility(true);
        return graph.intermediateUpdate(fragment.getUpNode(), fragment.getDownNode());
    }

    public Replace hide(@NotNull NewGraphFragment fragment) {
        if (!fragment.isVisible()) {
            throw new IllegalArgumentException("is hide fragment: " + fragment);
        }
        Edge upDownEdge = getUpDownEdge(fragment);
        if (upDownEdge != null) {
            graph.getVisibilityController().hideEdge(upDownEdge);
        }
        Edge edge = graph.getEdgeController().createEdge(fragment.getUpNode(), fragment.getDownNode(),
                fragment.getUpNode().getBranch(), Edge.Type.HIDE_FRAGMENT);
        hideFragments.put(edge, fragment);
        graph.getVisibilityController().hide(fragment.getIntermediateNodes());
        fragment.setVisibility(false);
        return graph.intermediateUpdate(fragment.getUpNode(), fragment.getDownNode());
    }

    @Nullable
    private Node commitNodeInRow(int rowIndex) {
        for (Node node : graph.getNodeRows().get(rowIndex).getNodes()) {
            if (node.getType() == Node.Type.COMMIT_NODE) {
                return node;
            }
        }
        return null;
    }

    public void hideAll() {
        int rowIndex = 0;
        while (rowIndex < graph.getNodeRows().size()) {
            Node node = commitNodeInRow(rowIndex);
            if (node != null) {
                NewGraphFragment fragment = fragmentGenerator.getMaximumDownFragment(node);
                if (fragment != null) {
                    hide(fragment);
                }
            }
            rowIndex++;
            if (rowIndex % 100 == 0) {
                System.out.println(rowIndex);
            }
        }
    }

}
