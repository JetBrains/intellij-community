package org.hanuna.gitalk.graphmodel.fragment;

import org.hanuna.gitalk.common.Get;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.mutable.GraphDecorator;
import org.hanuna.gitalk.graph.mutable.MutableGraph;
import org.hanuna.gitalk.graphmodel.FragmentManager;
import org.hanuna.gitalk.graphmodel.GraphFragment;
import org.hanuna.gitalk.graphmodel.fragment.elements.SimpleGraphFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author erokhins
 */
public class FragmentManagerImpl implements FragmentManager {
    private final MutableGraph graph;
    private final FragmentGenerator fragmentGenerator;
    private final Map<Edge, GraphFragment> hideFragments = new HashMap<Edge, GraphFragment>();
    private final FragmentGraphDecorator graphDecorator = new FragmentGraphDecorator();
    private final CallBackFunction callBackFunction;

    private boolean updateFlag = true;


    public FragmentManagerImpl(MutableGraph graph, CallBackFunction callBackFunction) {
        this.graph = graph;
        this.callBackFunction = callBackFunction;
        fragmentGenerator = new FragmentGenerator(graph);
    }

    public interface CallBackFunction {
        public UpdateRequest runIntermediateUpdate(@NotNull Node upNode, @NotNull Node downNode);
        public void fullUpdate();
    }

    @Override
    public void setUnhiddenNodes(@NotNull Get<Node, Boolean> unhiddenNodes) {
        fragmentGenerator.setUnhiddenNodes(unhiddenNodes);
    }

    public GraphDecorator getGraphDecorator() {
        return graphDecorator;
    }

    @Nullable
    private Edge getHideEdge(@NotNull Node node) {
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
    @Override
    public GraphFragment relateFragment(@NotNull GraphElement graphElement) {
        Node node = graphElement.getNode();
        if (node != null) {
            Edge edge = getHideEdge(node);
            if (edge != null) {
                GraphFragment fragment = hideFragments.get(edge);
                assert fragment != null;
                return fragment;
            } else {
                GraphFragment fragment = fragmentGenerator.getFragment(node);
                if (fragment != null && fragment.getDownNode().getRowIndex() >= node.getRowIndex()) {
                    return fragment;
                } else {
                    return null;
                }
            }
        } else {
            Edge edge = graphElement.getEdge();
            assert edge != null : "bad graphElement: edge & node is null";
            if (edge.getType() == Edge.Type.HIDE_FRAGMENT) {
                GraphFragment fragment = hideFragments.get(edge);
                assert fragment != null;
                return fragment;
            } else {
                GraphFragment fragment = fragmentGenerator.getFragment(edge.getUpNode());
                if (fragment != null && fragment.getDownNode().getRowIndex() >= edge.getDownNode().getRowIndex()) {
                    return fragment;
                } else {
                    return null;
                }
            }
        }
    }

    @NotNull
    private Edge getHideFragmentEdge(@NotNull GraphFragment fragment) {
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


    @NotNull
    @Override
    public UpdateRequest show(@NotNull GraphFragment fragment) {
        if (fragment.isVisible()) {
            throw new IllegalArgumentException("is not hide fragment: " + fragment);
        }
        graphDecorator.show(fragment, getHideFragmentEdge(fragment));
        ((SimpleGraphFragment) fragment).setVisibility(true);

        if (updateFlag) {
            return callBackFunction.runIntermediateUpdate(fragment.getUpNode(), fragment.getDownNode());
        } else {
            return UpdateRequest.ID_UpdateRequest;
        }
    }

    @NotNull
    @Override
    public UpdateRequest hide(@NotNull GraphFragment fragment) {
        if (!fragment.isVisible()) {
            throw new IllegalArgumentException("is hide fragment: " + fragment);
        }
        Edge edge = graphDecorator.hide(fragment);
        hideFragments.put(edge, fragment);
        ((SimpleGraphFragment) fragment).setVisibility(false);

        if (updateFlag) {
            return callBackFunction.runIntermediateUpdate(fragment.getUpNode(), fragment.getDownNode());
        } else {
            return UpdateRequest.ID_UpdateRequest;
        }
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

    @Override
    public void hideAll() {
        int rowIndex = 0;
        updateFlag = false;
        while (rowIndex < graph.getNodeRows().size()) {
            Node node = commitNodeInRow(rowIndex);
            if (node != null) {
                GraphFragment fragment = fragmentGenerator.getMaximumDownFragment(node);
                if (fragment != null) {
                    hide(fragment);
                }
            }
            rowIndex++;
        }
        updateFlag = true;
        callBackFunction.fullUpdate();
    }

}
