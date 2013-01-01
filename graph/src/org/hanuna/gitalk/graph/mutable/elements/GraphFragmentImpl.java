package org.hanuna.gitalk.graph.mutable.elements;

import org.hanuna.gitalk.graph.elements.GraphFragment;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static org.hanuna.gitalk.graph.mutable.MutableGraphUtils.firstDownEdge;

/**
 * @author erokhins
 */
public class GraphFragmentImpl implements GraphFragment {

    private final Node upNode;
    private final Node downNode;
    private final Set<Node> visitedNodes = new HashSet<Node>();

    public GraphFragmentImpl(Node upNode, Node downNode) {
        if (upNode.getDownEdges().size() != 1 || downNode.getUpEdges().size() != 1) {
            throw new IllegalStateException("bad GraphFragment up: " + upNode.getDownEdges().size() +
                    " down: " + downNode.getUpEdges().size());
        }
        if (downNode.getRowIndex() < upNode.getRowIndex()) {
            throw new IllegalArgumentException("up index: " + upNode.getRowIndex() +
                    " down index:" + downNode.getRowIndex());
        }
        this.downNode = downNode;
        this.upNode = upNode;
    }

    @NotNull
    @Override
    public Node getUpNode() {
        return upNode;
    }

    @NotNull
    @Override
    public Node getDownNode() {
        return downNode;
    }

    private boolean notVisited(@NotNull Node node) {
        if (node.getUpEdges().size() <= 1) {
            return true;
        }
        if (visitedNodes.contains(node)) {
            return false;
        } else {
            visitedNodes.add(node);
            return true;
        }
    }

    /**
     * @param node - not run graphRunnable.nodeRun(node) & not check count up edges
     */
    private void startRunner(@NotNull Node node, @NotNull GraphElementRunnable graphRunnable) {
        if (node == downNode) {
            return;
        }
        while (node.getDownEdges().size() == 1) {
            Edge edge = firstDownEdge(node);
            graphRunnable.edgeRun(edge);

            Node nextNode = edge.getDownNode();
            if (nextNode == downNode) {
                return;
            }
            if (notVisited(nextNode)) {
                graphRunnable.nodeRun(nextNode);
            }
            node = nextNode;
        }

        // i.e. node.getDownEdges().size() != 1 && node above downNode
        for (Edge edge : node.getDownEdges()) {
            graphRunnable.edgeRun(edge);
            Node next = edge.getDownNode();
            if (notVisited(next)) {
                graphRunnable.nodeRun(next);
                startRunner(next, graphRunnable);
            }
        }
    }

    @Override
    public void intermediateWalker(@NotNull GraphElementRunnable graphRunnable) {
        visitedNodes.clear();
        startRunner(upNode, graphRunnable);
    }
}
