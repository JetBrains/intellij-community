package org.hanuna.gitalk.graph.new_mutable.fragments;

import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.new_mutable.MutableGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author erokhins
 */
public class FragmentGenerator {
    private final MutableGraph graph;
    private Set<Node> unhiddenNodes = Collections.emptySet();

    public FragmentGenerator(MutableGraph graph) {
        this.graph = graph;
    }

    private void addDownNodeToSet(@NotNull Set<Node> nodes, @NotNull Node node) {
        for (Edge edge : node.getDownEdges()) {
            Node downNode = edge.getDownNode();
            nodes.add(downNode);
        }
    }

    private boolean allUpNodeHere(@NotNull Set<Node> here, @NotNull Node node) {
        for (Edge upEdge : node.getUpEdges()) {
            if (!here.contains(upEdge.getUpNode())) {
                return false;
            }
        }
        return true;
    }

    public void setUnhiddenNodes(Set<Node> unhiddenNodes) {
        this.unhiddenNodes = unhiddenNodes;
    }

    @Nullable
    public NewGraphFragment getShortFragment(@NotNull Node startNode) {
        if (startNode.getType() != Node.Type.COMMIT_NODE) {
            throw new IllegalArgumentException("small fragment may start only with COMMIT_NODE, but this node is: " + startNode);
        }

        Set<Node> upNodes = new HashSet<Node>();
        upNodes.add(startNode);
        Set<Node> notAddedNodes = new HashSet<Node>();
        addDownNodeToSet(notAddedNodes, startNode);

        Node endNode = null;

        int startRowIndex = startNode.getRowIndex() + 1;
        int lastIndex = graph.getNodeRows().size() - 1;

        boolean isEnd = false;
        for (int currentRowIndex = startRowIndex; currentRowIndex <= lastIndex && !isEnd; currentRowIndex++) {
            for (Node node : graph.getNodeRows().get(currentRowIndex).getNodes()) {
                if (notAddedNodes.remove(node)) {
                    if (notAddedNodes.isEmpty() && node.getType() == Node.Type.COMMIT_NODE) {
                        if (allUpNodeHere(upNodes, node)) { // i.e. we found smallFragment
                            endNode = node;
                        }
                        isEnd = true;
                        break;
                    } else {
                        if (!allUpNodeHere(upNodes, node) || unhiddenNodes.contains(node)) {
                            isEnd = true;
                        }
                        upNodes.add(node);
                        addDownNodeToSet(notAddedNodes, node);
                    }
                }
            }
        }
        if (endNode == null) {
            return null;
        } else {
            upNodes.remove(startNode);
            return new SimpleGraphFragment(startNode, endNode, upNodes);
        }
    }



}
