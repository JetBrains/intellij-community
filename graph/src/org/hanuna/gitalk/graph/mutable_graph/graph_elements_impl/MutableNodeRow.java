package org.hanuna.gitalk.graph.mutable_graph.graph_elements_impl;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.graph_elements.NodeRow;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableNodeRow implements NodeRow {
    private final List<MutableNode> allNodes = new ArrayList<MutableNode>(2);
    private final List<MutableNode> visibleNodes = new ArrayList<MutableNode>(2);
    private final int logIndex;
    private int rowIndex;


    public MutableNodeRow(int logIndex) {
        this.logIndex = logIndex;
        this.rowIndex = logIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public void add(MutableNode node) {
        allNodes.add(node);
        if (node.isVisible()) {
            visibleNodes.add(node);
        }
    }

    public boolean hasVisibleNodes() {
        return ! visibleNodes.isEmpty();
    }

    public void updateVisibleNodes() {
        visibleNodes.clear();
        for (MutableNode node : allNodes) {
            if (node.isVisible()) {
                visibleNodes.add(node);
            }
        }
    }

    @Override
    public int getRowIndex() {
        return rowIndex;
    }

    public int getLogIndex() {
        return logIndex;
    }

    @NotNull
    @Override
    public ReadOnlyList<Node> getVisibleNodes() {
        return ReadOnlyList.<Node>newReadOnlyList(visibleNodes);
    }

}
