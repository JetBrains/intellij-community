package org.hanuna.gitalk.graph.mutable_graph.graph_controller;

import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.GraphPiece;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.graph.mutable_graph.MutableGraph;
import org.hanuna.gitalk.graph.mutable_graph.MutableGraphUtils;
import org.jetbrains.annotations.NotNull;

import static org.hanuna.gitalk.graph.mutable_graph.MutableGraphUtils.*;

/**
 * @author erokhins
 */
public class GraphPieceImpl implements GraphPiece {
    private boolean visible = true;
    private boolean selected = false;

    private final MutableGraph graph;

    private final Node upVisible;
    private final Node downVisible;

    private final Node upHide;
    private final Node downHide;

    public GraphPieceImpl(MutableGraph graph, Node upVisible, Node downVisible) {
        if (upVisible.getDownEdges().size() != 1 || downVisible.getUpEdges().size() != 1) {
            throw new IllegalStateException("bad GraphPiece up: " + upVisible.getDownEdges().size() +
                    " down: " + downVisible.getUpEdges().size());
        }
        this.downVisible = downVisible;
        this.upVisible = upVisible;
        upHide = firstDownEdge(upVisible).getDownNode();
        downHide = downVisible.getUpEdges().get(0).getUpNode();

        assert upHide.getRowIndex() <= downHide.getRowIndex() : "up: " + upHide.getRowIndex() +
                " down: " + downHide.getRowIndex()  ;
        this.graph = graph;
    }

    private void walkRunner(@NotNull Node up, @NotNull Node down, @NotNull Runner runner) {
        Node currentNode = up;
        while (currentNode != down) {
            runner.nodeRun(currentNode);
            assert currentNode.getDownEdges().size() == 1;
            Edge downEdge = firstDownEdge(currentNode);
            runner.edgeRun(downEdge);
            currentNode = downEdge.getDownNode();
        }
        runner.nodeRun(down);
    }

    @Override
    public boolean visible() {
        return visible;
    }

    @Override
    public Replace setVisible(final boolean visible) {
        if (this.visible == visible) {
            return null;
        }
        this.visible = visible;
        walkRunner(upHide, downHide, new Runner() {
            @Override
            public void edgeRun(@NotNull Edge edge) {
                // do nothing
            }

            @Override
            public void nodeRun(@NotNull Node node) {
                MutableGraphUtils.setVisible(node, visible);
            }
        });
        if (visible) {
            final Edge edge = firstDownEdge(upVisible);
            assert edge.getType() == Edge.Type.HIDE_PIECE;

            removeHidePiece(edge);
            removeEdge(edge);
            createEdge(upVisible, upHide, Edge.Type.USUAL, upVisible.getBranch());
            createEdge(downHide, downVisible, Edge.Type.USUAL, upVisible.getBranch());
        } else {
            removeEdge(firstDownEdge(upVisible));
            removeEdge(firstDownEdge(downHide));
            Edge edge = createEdge(upVisible, downVisible, Edge.Type.HIDE_PIECE, upVisible.getBranch());
            addHidePiece(edge, this);
        }
        return graph.fixRowIndex(upVisible.getRowIndex(), downVisible.getRowIndex());
    }

    @Override
    public boolean selected() {
        return selected;
    }

    @Override
    public void setSelected(final boolean selected) {
        if (this.selected == selected) {
            return;
        }
        this.selected = selected;
        walkRunner(upVisible, downVisible, new Runner() {
            @Override
            public void edgeRun(@NotNull Edge edge) {
                MutableGraphUtils.setSelected(edge, selected);
            }

            @Override
            public void nodeRun(@NotNull Node node) {
                MutableGraphUtils.setSelected(node, selected);
            }
        });
    }


    private interface Runner {
        public void edgeRun(@NotNull Edge edge);
        public void nodeRun(@NotNull Node node);
    }
}
