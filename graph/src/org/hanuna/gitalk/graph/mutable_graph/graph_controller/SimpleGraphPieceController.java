package org.hanuna.gitalk.graph.mutable_graph.graph_controller;

import org.hanuna.gitalk.graph.GraphFragment;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.graph.mutable_graph.MutableGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.hanuna.gitalk.graph.mutable_graph.MutableGraphUtils.*;

/**
 * @author erokhins
 */
public class SimpleGraphPieceController implements GraphPieceController {
    private final MutableGraph graph;



    public SimpleGraphPieceController(MutableGraph graph) {
        this.graph = graph;
    }

    private boolean simpleNode(@NotNull Node node) {
        return node.getUpEdges().size() <= 1 && node.getDownEdges().size() <= 1;
    }

    /**
     * @return null, if node is not simple
     */
    @Nullable
    private Node downSimpleNode(@NotNull Node node) {
        if (!simpleNode(node)) {
            return null;
        }
        while (node.getDownEdges().size() == 1) {
            Node nextNode = firstDownEdge(node).getDownNode();
            if (simpleNode(nextNode)) {
                node = nextNode;
            } else {
                return node;
            }
        }
        return node;
    }

    /**
     * @return null, if node is not simple
     */
    @Nullable
    private Node upSimpleNode(@NotNull Node node) {
        if (!simpleNode(node)) {
            return null;
        }
        while (node.getUpEdges().size() == 1) {
            Node prevNode = firstUpEdge(node).getUpNode();
            if (simpleNode(prevNode)) {
                node = prevNode;
            } else {
                return node;
            }
        }
        return node;
    }

    private GraphFragment createGraphPiece(@NotNull Node up, @NotNull Node down) {
        return new GraphFragmentImpl(up, down);
    }

    private GraphFragment relatePiece(@NotNull Node node) {
        if (node.getDownEdges().size() == 1) {
            Edge edge = firstDownEdge(node);
            if (edge.getType() == Edge.Type.HIDE_PIECE) {
                return getHidePiece(edge);
            }
        }
        if (node.getUpEdges().size() == 1) {
            Edge edge = firstUpEdge(node);
            if (edge.getType() == Edge.Type.HIDE_PIECE) {
                return getHidePiece(edge);
            }
        }
        if (!simpleNode(node)) {
            return null;
        }
        Node up = upSimpleNode(node);
        Node down = downSimpleNode(node);
        assert up != null && down != null;

        if (up != down && firstDownEdge(up).getDownNode() != down) {
            return createGraphPiece(up, down);
        } else {
            return null;
        }
    }

    private GraphFragment relatePiece(@NotNull Edge edge) {
        if (edge.getType() == Edge.Type.HIDE_PIECE) {
            return getHidePiece(edge);
        }
        Node up = upSimpleNode(edge.getUpNode());
        Node down = downSimpleNode(edge.getDownNode());
        if (up == null || down == null) {
            return null;
        }
        return createGraphPiece(up, down);
    }

    @Nullable
    @Override
    public GraphFragment relatePiece(@NotNull GraphElement graphElement) {
        Node node = graphElement.getNode();
        if (node != null) {
            return relatePiece(node);
        }
        Edge edge = graphElement.getEdge();
        if (edge != null) {
            return relatePiece(edge);
        }
        throw new IllegalStateException("unexpected graphElement");
    }
}
