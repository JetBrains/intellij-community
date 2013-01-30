package org.hanuna.gitalk.graph.new_mutable.elements;

import org.hanuna.gitalk.common.OneElementList;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.hanuna.gitalk.graph.new_mutable.ElementVisibilityController;
import org.hanuna.gitalk.graph.new_mutable.MutableGraph;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableNodeRow implements NodeRow {
    private final List<Node> nodes = new OneElementList<Node>();
    private final MutableGraph graph;
    private int rowIndex;

    public MutableNodeRow(MutableGraph graph, int rowIndex) {
        this.graph = graph;
        this.rowIndex = rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public void addNode(@NotNull Node node) {
        nodes.add(node);
    }

    public MutableGraph getMutableGraph() {
        return graph;
    }

    public boolean hasVisibleNodes() {
        return !getNodes().isEmpty();
    }

    @NotNull
    @Override
    public List<Node> getNodes() {
        List<Node> visibleNodes = new ArrayList<Node>(nodes.size());
        ElementVisibilityController visibilityController = graph.getVisibilityController();
        for (Node node : nodes) {
            if (visibilityController.isVisible(node)) {
                visibleNodes.add(node);
            }
        }
        return visibleNodes;
    }

    @Override
    public int getRowIndex() {
        return rowIndex;
    }
}
