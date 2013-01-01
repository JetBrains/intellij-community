package org.hanuna.gitalk.graph.mutable.elements;

import org.hanuna.gitalk.common.OneElementList;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableNodeRow implements NodeRow {
    private final List<MutableNode> allNodes = new OneElementList<MutableNode>();
    private final List<MutableNode> visibleNodes = new OneElementList<MutableNode>();
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
    public List<Node> getVisibleNodes() {
        return Collections.<Node>unmodifiableList(visibleNodes);
    }

}
