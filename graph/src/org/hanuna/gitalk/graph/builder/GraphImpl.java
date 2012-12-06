package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.*;
import org.hanuna.gitalk.graph.graph_elements.Branch;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class GraphImpl implements Graph {
    private final List<MutableNodeRow> rows;
    private final int lastLogIndex;

    public GraphImpl(List<MutableNodeRow> rows, int lastLogIndex) {
        this.rows = rows;
        this.lastLogIndex = lastLogIndex;
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
        boolean allRight;
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

        MutableNode.createEdge(upNode, downNode, Edge.Type.HIDE_PIECE, branch);
    }

    private void fixRowIndexs() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setRowIndex(i);
        }
    }

    @Override
    public void hideBranch(@NotNull Node upNode, @NotNull Node downNode) {
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
    }

    /**
     * @param commit Convention: commit.getData() != null && commit.getData().getParents().size() > 0
     */
    private Commit getParent(Commit commit) {
        final CommitData data = commit.getData();
        assert data != null;
        assert data.getParents().size() == 1;
        return data.getParents().get(0);
    }

    @Override
    public void showBranch(@NotNull Edge edge) {
        assert edge.getType() == Edge.Type.HIDE_PIECE : "not hide branch";
        MutableNode upNode = (MutableNode) edge.getUpNode();
        MutableNode downNode = (MutableNode) edge.getDownNode();
        upNode.removeDownEdge(edge);
        downNode.removeUpEdge(edge);
        Branch branch = edge.getBranch();

        AddedNodes added = new AddedNodes(upNode.getRowIndex());
        MutableNode prevNode = upNode;
        Commit t = getParent(upNode.getCommit());
        while (t != downNode.getCommit()) {
            MutableNode newNode = added.addNewNode(t, branch);
            MutableNode.createEdge(prevNode, newNode, Edge.Type.USUAL, branch);
            prevNode = newNode;
            t = getParent(t);
        }
        MutableNode.createEdge(prevNode, downNode, Edge.Type.USUAL, branch);
        fixRowIndexs();
    }

    private class AddedNodes {
        private int currentRowIndex;

        private AddedNodes(int currentRowIndex) {
            this.currentRowIndex = currentRowIndex;
        }

        private int logIndexOfRow(NodeRow row) {
            ReadOnlyList<Node> nodes = row.getNodes();
            assert nodes.size() > 0;
            CommitData data = nodes.get(0).getCommit().getData();
            if (data == null) {
                return lastLogIndex;
            } else {
                return data.getLogIndex();
            }
        }

        public MutableNode addNewNode(Commit commit, Branch branch) {
            assert commit.getData() != null;
            int searchLogIndex = commit.getData().getLogIndex();
            MutableNode node = new MutableNode(commit, branch);
            node.setType(Node.Type.COMMIT_NODE);
            int currentLogIndex = rows.get(currentRowIndex).getRowLogIndex();
            while (currentLogIndex < searchLogIndex) {
                currentRowIndex++;
                currentLogIndex = rows.get(currentRowIndex).getRowLogIndex();
            }
            MutableNodeRow row;
            if (currentLogIndex == searchLogIndex) {
                row = rows.get(currentRowIndex);
            } else {
                row = new MutableNodeRow(searchLogIndex, currentRowIndex);
                rows.add(currentRowIndex, row);
            }
            node.setRow(row);
            row.add(node);
            return node;
        }
    }

}
