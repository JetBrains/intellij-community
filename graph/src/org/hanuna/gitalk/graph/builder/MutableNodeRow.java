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
    private final List<Node> nodes = new ArrayList<Node>(2);;
    private final int logIndex;


    public MutableNodeRow(int logIndex) {
        this.logIndex = logIndex;
    }

    public void add(Node node) {
        nodes.add(node);
    }

    @NotNull
    @Override
    public ReadOnlyList<Node> getNodes() {
        return ReadOnlyList.newReadOnlyList(nodes);
    }

    @Override
    public int getLogIndex() {
        return 0;
    }

}
