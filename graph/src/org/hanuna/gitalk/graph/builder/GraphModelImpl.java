package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.common.Interval;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.graph.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class GraphModelImpl implements GraphModel {
    private final RemoveIntervalArrayList<MutableNodeRow> rows;

    public GraphModelImpl(RemoveIntervalArrayList<MutableNodeRow> rows) {
        this.rows = rows;
    }



    @NotNull
    @Override
    public ReadOnlyList<NodeRow> getNodeRows() {
        return ReadOnlyList.<NodeRow>newReadOnlyList(rows);
    }

    /**
     * @param node conviction: node.getDownEdges().size() > 0
     * @throws NullPointerException if convention is false
     */
    private MutableNode nextNode(MutableNode node) {
        return (MutableNode) node.getDownEdges().get(0).getDownNode();
    }

    private void checkHideBranch(MutableNode upNode, MutableNode downNode) {
        boolean allRight = true;
        allRight = (upNode.getDownEdges().size() == 1) && (downNode.getUpEdges().size() == 1);
        if (allRight) {
            MutableNode t = nextNode(upNode);
            while (allRight && (t != downNode)) {
                allRight = allRight && (t.getDownEdges().size() == 1) && (t.getUpEdges().size() == 1);
                if (allRight) {
                    t = nextNode(t);
                }
            }
        }
        if (!allRight) {
            throw new IllegalArgumentException();
        }
    }

    private void fixEdge(MutableNode upNode, MutableNode downNode) {
        Edge up = upNode.getDownEdges().get(0);
        Branch branch = up.getBranch();
        upNode.removeDownEdge(up);

        Edge down = downNode.getUpEdges().get(0);
        downNode.removeUpEdge(down);

        MutableNode.createEdge(upNode, downNode, Edge.Type.hideBranch, branch);
    }

    private void fixRowIndexs() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setRowIndex(i);
        }
    }

    @NotNull
    @Override
    public Interval hideBranch(@NotNull Node upNode, @NotNull Node downNode) {
        MutableNode upMutableNode = (MutableNode) upNode;
        MutableNode downMutableNode = (MutableNode) downNode;
        checkHideBranch(upMutableNode, downMutableNode);
        MutableNode t = nextNode(upMutableNode);
        while (t != downMutableNode) {
            MutableNodeRow row = t.getRow();
            row.remove(t);
            if (row.isEmpty()) {
                rows.remove(row);
            }
            t = nextNode(t);
        }
        fixEdge(upMutableNode, downMutableNode);
        fixRowIndexs();
        return new Interval(upMutableNode.getRowIndex(), downMutableNode.getRowIndex());
    }

    @NotNull
    @Override
    public Interval showBranch(@NotNull Edge edge) {
        return null;
    }

}
