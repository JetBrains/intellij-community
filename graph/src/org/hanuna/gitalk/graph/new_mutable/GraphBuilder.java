package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.graph.elements.Branch;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.new_mutable.elements.MutableNode;
import org.hanuna.gitalk.graph.new_mutable.elements.MutableNodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author erokhins
 */
public class GraphBuilder {
    public static MutableGraph build(@NotNull List<Commit> commits) {
        Map<Commit, Integer> commitLogIndexes = new HashMap<Commit, Integer>(commits.size());
        for (int i = 0; i < commits.size(); i++) {
            commitLogIndexes.put(commits.get(i), i);
        }
        GraphBuilder builder = new GraphBuilder(commits.size() - 1, commitLogIndexes);
        return builder.runBuild(commits);
    }

    private final int lastLogIndex;
    private final Map<Commit, Integer> commitLogIndexes;
    private final MutableGraph graph = new MutableGraph();

    private final Map<Commit, UnderdoneNode> notAddedNodes = new HashMap<Commit, UnderdoneNode>();
    private MutableNodeRow nextRow;

    private GraphBuilder(int lastLogIndex, @NotNull Map<Commit, Integer> commitLogIndexes) {
        this.lastLogIndex = lastLogIndex;
        this.commitLogIndexes = commitLogIndexes;
    }

    private int getLogIndexOfCommit(@NotNull Commit commit) {
        if (commit.getParents() == null) {
            return lastLogIndex + 1;
        } else {
            return commitLogIndexes.get(commit);
        }
    }

    private class UnderdoneNode {
        private final Branch branch;
        private final Node firstUpNode;
        private Node secondUpNode = null;
        private Branch secondEdgeBranch = null;

        private UnderdoneNode(@NotNull Branch branch, @NotNull Node firstUpNode) {
            this.branch = branch;
            this.firstUpNode = firstUpNode;
        }

        @NotNull
        public Branch getBranch() {
            return branch;
        }

        @NotNull
        public Node getFirstUpNode() {
            return firstUpNode;
        }


        public Branch getSecondEdgeBranch() {
            return secondEdgeBranch;
        }

        public void setSecondEdgeBranch(Branch secondEdgeBranch) {
            this.secondEdgeBranch = secondEdgeBranch;
        }

        public void setSecondUpNode(Node secondUpNode) {
            this.secondUpNode = secondUpNode;
        }

        public Node getSecondUpNode() {
            return secondUpNode;
        }

    }

    private void createUpEdges(@NotNull Node node, @NotNull UnderdoneNode underdoneNode) {
        graph.getEdgeController().createEdge(underdoneNode.getFirstUpNode(),
                node, underdoneNode.getBranch(), Edge.Type.USUAL);

        if (underdoneNode.getSecondUpNode() != null) {
            graph.getEdgeController().createEdge(underdoneNode.getSecondUpNode(), node,
                    underdoneNode.getSecondEdgeBranch(), Edge.Type.USUAL);
        }
    }

    private Node addCurrentCommitAndFinishRow(@NotNull Commit commit) {
        UnderdoneNode underdoneNode = notAddedNodes.remove(commit);
        Node node;
        if (underdoneNode == null) {
            node = new MutableNode(nextRow, new Branch(commit), commit, Node.Type.COMMIT_NODE);
        } else {
            node = new MutableNode(nextRow, underdoneNode.getBranch(), commit, Node.Type.COMMIT_NODE);
            createUpEdges(node, underdoneNode);
        }

        nextRow.addNode(node);
        graph.addRow(nextRow);
        nextRow = new MutableNodeRow(graph, nextRow.getRowIndex() + 1);
        return node;
    }

    private void addParent(Node node, Commit parentCommit, Branch branch) {
        UnderdoneNode underdoneNode = notAddedNodes.remove(parentCommit);
        if (underdoneNode == null) {
            notAddedNodes.put(parentCommit, new UnderdoneNode(branch, node));
        } else {
            underdoneNode.setSecondUpNode(node);
            underdoneNode.setSecondEdgeBranch(branch);

            int parentRowIndex = getLogIndexOfCommit(parentCommit);
            // i.e. we need of create EDGE_NODE node
            if (nextRow.getRowIndex() != parentRowIndex) {
                Node edgeNode = new MutableNode(nextRow, underdoneNode.getBranch(), parentCommit, Node.Type.EDGE_NODE);
                createUpEdges(edgeNode, underdoneNode);
                nextRow.addNode(edgeNode);

                notAddedNodes.put(parentCommit, new UnderdoneNode(underdoneNode.getBranch(), edgeNode));
            } else {
                // i.e. node must be added in nextRow, when addCurrentCommitAndFinishRow() will called in next time
                notAddedNodes.put(parentCommit, underdoneNode);
            }
        }
    }

    private void append(@NotNull Commit commit) {
        Node node = addCurrentCommitAndFinishRow(commit);

        List<Commit> parents = commit.getParents();
        if (parents == null) {
            throw new IllegalStateException("commit was append, but commit parents is null");
        }
        if (parents.size() == 1) {
            addParent(node, parents.get(0), node.getBranch());
        } else {
            for (Commit parent : parents) {
                addParent(node, parent, new Branch(node.getCommit(), parent));
            }
        }
    }

    private void prepare() {
        nextRow = new MutableNodeRow(graph, 0);
    }

    private void lastActions() {
        Set<Commit> notReadiedCommits = notAddedNodes.keySet();
        for (Commit commit : notReadiedCommits) {
            UnderdoneNode underdoneNode = notAddedNodes.get(commit);
            Node node = new MutableNode(nextRow, underdoneNode.getBranch(), commit, Node.Type.END_COMMIT_NODE);
            createUpEdges(node, underdoneNode);
            nextRow.addNode(node);
        }
        if (nextRow.hasVisibleNodes()) {
            graph.addRow(nextRow);
        }
    }

    @NotNull
    private MutableGraph runBuild(@NotNull List<Commit> commits) {
        if (commits.size() == 0) {
            throw new IllegalArgumentException("Empty list commits");
        }
        prepare();
        for (Commit commit : commits) {
            append(commit);
        }
        lastActions();
        graph.updateVisibleRows();
        return graph;
    }

}
