package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.common.Interval;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.graph.Edge;
import org.hanuna.gitalk.graph.GraphModel;
import org.hanuna.gitalk.graph.Node;
import org.hanuna.gitalk.graph.NodeRow;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class GraphModelImpl implements GraphModel {
    private final RemoveIntervalArrayList<MutableNodeRow> rows;

    public GraphModelImpl(RemoveIntervalArrayList<MutableNodeRow> rows) {
        this.rows = rows;
    }


    @Override
    public int getCurrentRowIndex(int logRowIndex) {
        return 0;
    }

    @NotNull
    @Override
    public ReadOnlyList<NodeRow> getNodeRows() {
        return ReadOnlyList.<NodeRow>newReadOnlyList(rows);
    }

    @NotNull
    @Override
    public Interval showBranch(Edge edge) {
        return null;
    }

    @NotNull
    @Override
    public Interval hideBranch(Node upNode, Node downNode) {
        return null;
    }

}
