package org.hanuna.gitalk.graphmodel.fragment;

import org.hanuna.gitalk.common.Function;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graphmodel.GraphFragment;
import org.hanuna.gitalk.graphmodel.fragment.elements.SimpleGraphFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author erokhins
 */
public class FragmentGenerator {
    private static final int SEARCH_LIMIT = 20; // 20 nodes

    private final ShortFragmentGenerator shortFragmentGenerator;
    private Function<Node, Boolean> unhiddenNodes = new Function<Node, Boolean>() {
        @NotNull
        @Override
        public Boolean get(@NotNull Node key) {
            return false;
        }
    };

    public FragmentGenerator(Graph graph) {
        shortFragmentGenerator = new ShortFragmentGenerator(graph);
    }

    public void setUnhiddenNodes(Function<Node, Boolean> unhiddenNodes) {
        shortFragmentGenerator.setUnhiddenNodes(unhiddenNodes);
        this.unhiddenNodes = unhiddenNodes;
    }

    public GraphFragment getFragment(@NotNull Node node) {
        int countTry = SEARCH_LIMIT;
        GraphFragment downFragment = null;
        while (countTry > 0 && ((downFragment = getMaximumDownFragment(node)) == null)) {
            countTry--;
            if (node.getUpEdges().isEmpty()) {
                return null;
            } else {
                node = node.getUpEdges().get(0).getUpNode();
            }
        }
        if (downFragment == null) {
            return null;
        }
        GraphFragment upFragment = getMaximumUpFragment(node);
        if (upFragment == null) {
            return downFragment;
        } else {
            Set<Node> intermediateNodes = new HashSet<Node>(downFragment.getIntermediateNodes());
            intermediateNodes.addAll(upFragment.getIntermediateNodes());
            intermediateNodes.add(node);
            return new SimpleGraphFragment(upFragment.getUpNode(), downFragment.getDownNode(), intermediateNodes);
        }
    }

    @Nullable
    public GraphFragment getMaximumDownFragment(@NotNull Node startNode) {
        if (startNode.getType() != Node.NodeType.COMMIT_NODE) {
            return null;
        }
        GraphFragment fragment = shortFragmentGenerator.getDownShortFragment(startNode);
        if (fragment == null) {
            return null;
        }
        Set<Node> intermediateNodes = new HashSet<Node>(fragment.getIntermediateNodes());
        Node endNode = fragment.getDownNode();
        while ((fragment = shortFragmentGenerator.getDownShortFragment(endNode)) != null
                && !unhiddenNodes.get(endNode)) {
            intermediateNodes.addAll(fragment.getIntermediateNodes());
            intermediateNodes.add(endNode);
            endNode = fragment.getDownNode();
        }
        if (intermediateNodes.isEmpty()) {
            return null;
        } else {
            return new SimpleGraphFragment(startNode, endNode, intermediateNodes);
        }
    }


    @Nullable
    public GraphFragment getMaximumUpFragment(@NotNull Node startNode) {
        GraphFragment fragment = shortFragmentGenerator.getUpShortFragment(startNode);
        if (fragment == null) {
            return null;
        }
        Set<Node> intermediateNodes = new HashSet<Node>(fragment.getIntermediateNodes());
        Node endNode = fragment.getDownNode();
        while ((fragment = shortFragmentGenerator.getUpShortFragment(endNode)) != null
                && !unhiddenNodes.get(endNode)) {
            intermediateNodes.addAll(fragment.getIntermediateNodes());
            intermediateNodes.add(endNode);
            endNode = fragment.getUpNode();
        }
        if (intermediateNodes.isEmpty()) {
            return null;
        } else {
            return new SimpleGraphFragment(endNode, startNode, intermediateNodes);
        }
    }

}
