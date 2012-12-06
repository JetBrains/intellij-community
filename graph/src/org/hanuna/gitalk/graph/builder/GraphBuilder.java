package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.graph_elements.Branch;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static org.hanuna.gitalk.graph.builder.MutableNode.createEdge;

/**
 * @author erokhins
 */
public class GraphBuilder {
    private int lastLogIndex;
    private Map<Hash, MutableNode> notAddedNodes = new HashMap<Hash, MutableNode>();
    private MutableNodeRow nextRow;
    private final List<MutableNodeRow> rows = new ArrayList<MutableNodeRow>();



    private int getLogIndexOfCommit(Commit commit) {
        final CommitData data = commit.getData();
        if (data == null) {
            return lastLogIndex;
        } else {
            return data.getLogIndex();
        }
    }



    // return added node
    private MutableNode addCurrentCommitAndFinishRow(Commit commit) {
        MutableNode node = notAddedNodes.remove(commit.hash());
        if (node == null) {
            node = new MutableNode(commit, new Branch());
        }
        node.setRow(nextRow);
        node.setType(Node.Type.COMMIT_NODE);
        nextRow.add(node);
        rows.add(nextRow);

        nextRow = new MutableNodeRow(rows.size(), rows.size());
        return node;
    }

    private void addParent(MutableNode node, Commit parent, Branch branch) {
        MutableNode parentNode = notAddedNodes.get(parent.hash());
        if (parentNode == null) {
            parentNode = new MutableNode(parent, branch);
            createEdge(node, parentNode, Edge.Type.USUAL, branch);
            notAddedNodes.put(parent.hash(), parentNode);
        } else {
            int index = getLogIndexOfCommit(parent);
            createEdge(node, parentNode, Edge.Type.USUAL, branch);
            // i.e. we need create new Node
            if (index != rows.size()) {
                // remove old node
                notAddedNodes.remove(parent.hash());

                MutableNode newParentNode = new MutableNode(parentNode.getCommit(), parentNode.getBranch());
                createEdge(parentNode, newParentNode, Edge.Type.USUAL, parentNode.getBranch());
                notAddedNodes.put(parent.hash(), newParentNode);

                parentNode.setType(Node.Type.EDGE_NODE);
                parentNode.setRow(nextRow);
                nextRow.add(parentNode);
            } else {
                // i.e. it was real node. Fix branch if necessary.
                if (branch.younger(parentNode.getBranch())) {
                    parentNode.setBranch(branch);
                }
            }
        }
    }

    private void append(@NotNull Commit commit) {
        MutableNode node = addCurrentCommitAndFinishRow(commit);

        CommitData data = commit.getData();
        if (data == null) {
            throw new IllegalStateException("commit was append, but commitData is null");
        }
        final ReadOnlyList<Commit> parents = data.getParents();
        for (int i = 0; i < parents.size(); i++) {
            Commit parent = parents.get(i);
            if (i == 0) {
                addParent(node, parent, node.getBranch());
            } else {
                addParent(node, parent, new Branch());
            }
        }
    }

    private void prepare(int lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
        nextRow = new MutableNodeRow(0, 0);
    }

    private void lastActions() {
        final Collection<MutableNode> lastNodes = notAddedNodes.values();
        for (MutableNode node : lastNodes) {
            node.setRow(nextRow);
            node.setType(Node.Type.END_COMMIT_NODE);
            nextRow.add(node);
        }
        if (nextRow.getNodes().size() > 0) {
            rows.add(nextRow);
        }
    }

    @NotNull
    public Graph build(ReadOnlyList<Commit> commits) {
        prepare(commits.size() - 1);
        for (Commit commit : commits) {
            append(commit);
        }
        lastActions();
        return new GraphImpl(rows, lastLogIndex);
    }



}
