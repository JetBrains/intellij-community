package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.graph.Node;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.graph.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableNodeRow implements NodeRow {
    private final List<MutableNode> nodes = new ArrayList<MutableNode>(2);
    private final int rowLogIndex;
    private int rowIndex;


    public MutableNodeRow(int rowLogIndex, int rowIndex) {
        this.rowLogIndex = rowLogIndex;
        this.rowIndex = rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public void add(MutableNode node) {
        nodes.add(node);
    }

    public void remove(MutableNode node) {
        nodes.remove(node);
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public int getRowLogIndex() {
        return rowLogIndex;
    }

    @NotNull
    @Override
    public ReadOnlyList<Node> getNodes() {
        return ReadOnlyList.<Node>newReadOnlyList(nodes);
    }


}
