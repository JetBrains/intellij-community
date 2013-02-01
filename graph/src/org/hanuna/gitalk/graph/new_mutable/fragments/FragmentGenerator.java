package org.hanuna.gitalk.graph.new_mutable.fragments;

import org.hanuna.gitalk.graph.NewGraph;
import org.hanuna.gitalk.graph.elements.Node;
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

    public FragmentGenerator(NewGraph graph) {
        shortFragmentGenerator = new ShortFragmentGenerator(graph);
    }

    public void setUnhiddenNodes(UnhiddenNodeFunction unhiddenNodes) {
        shortFragmentGenerator.setUnhiddenNodes(unhiddenNodes);
    }

    public NewGraphFragment getFragment(@NotNull Node node) {
        int countTry = SEARCH_LIMIT;
        NewGraphFragment downFragment = null;
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
        NewGraphFragment upFragment = getMaximumUpFragment(node);
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
    public NewGraphFragment getMaximumDownFragment(@NotNull Node startNode) {
        if (startNode.getType() != Node.Type.COMMIT_NODE) {
            return null;
        }
        NewGraphFragment fragment = shortFragmentGenerator.getDownShortFragment(startNode);
        if (fragment == null) {
            return null;
        }
        Set<Node> intermediateNodes = new HashSet<Node>(fragment.getIntermediateNodes());
        Node endNode = fragment.getDownNode();
        while ((fragment = shortFragmentGenerator.getDownShortFragment(endNode)) != null) {
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
    public NewGraphFragment getMaximumUpFragment(@NotNull Node startNode) {
        NewGraphFragment fragment = shortFragmentGenerator.getUpShortFragment(startNode);
        if (fragment == null) {
            return null;
        }
        Set<Node> intermediateNodes = new HashSet<Node>(fragment.getIntermediateNodes());
        Node endNode = fragment.getDownNode();
        while ((fragment = shortFragmentGenerator.getUpShortFragment(endNode)) != null) {
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
