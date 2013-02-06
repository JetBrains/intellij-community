package org.hanuna.gitalk.graph.mutable;

import org.hanuna.gitalk.log.commit.Commit;
import org.hanuna.gitalk.log.commit.Hash;
import org.hanuna.gitalk.graph.elements.Branch;
import org.hanuna.gitalk.graph.mutable.elements.MutableNode;
import org.hanuna.gitalk.graph.mutable.elements.MutableNodeRow;
import org.hanuna.gitalk.graph.mutable.elements.UsualEdge;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hanuna.gitalk.graph.elements.Node.Type.*;

/**
 * @author erokhins
 */
public class GraphBuilder {
    public static MutableGraph build(@NotNull List<Commit> commits) {
        Map<Hash, Integer> commitLogIndexes = new HashMap<Hash, Integer>(commits.size());
        for (int i = 0; i < commits.size(); i++) {
            commitLogIndexes.put(commits.get(i).getCommitHash(), i);
        }
        GraphBuilder builder = new GraphBuilder(commits.size() - 1, commitLogIndexes);
        return builder.runBuild(commits);
    }

    public static void addCommitsToGraph(@NotNull MutableGraph graph, @NotNull List<Commit> commits) {
        new GraphAppendBuilder(graph).appendToGraph(commits);
    }

    // local package
    static void createUsualEdge(@NotNull MutableNode up, @NotNull MutableNode down, @NotNull Branch branch) {
        UsualEdge edge = new UsualEdge(up, down, branch);
        up.getInnerDownEdges().add(edge);
        down.getInnerUpEdges().add(edge);
    }

    private final int lastLogIndex;
    private final MutableGraph graph;
    private final Map<Hash, MutableNode> underdoneNodes;
    private Map<Hash, Integer> commitHashLogIndexes;

    private MutableNodeRow nextRow;

    public GraphBuilder(int lastLogIndex, Map<Hash, Integer> commitHashLogIndexes, MutableGraph graph,
                        Map<Hash, MutableNode> underdoneNodes, MutableNodeRow nextRow) {
        this.lastLogIndex = lastLogIndex;
        this.commitHashLogIndexes = commitHashLogIndexes;
        this.graph = graph;
        this.underdoneNodes = underdoneNodes;
        this.nextRow = nextRow;
    }

    public GraphBuilder(int lastLogIndex, Map<Hash, Integer> commitHashLogIndexes, MutableGraph graph) {
        this(lastLogIndex, commitHashLogIndexes, graph, new HashMap<Hash, MutableNode>(), new MutableNodeRow(graph, 0));
    }

    public GraphBuilder(int lastLogIndex, Map<Hash, Integer> commitHashLogIndexes) {
        this(lastLogIndex, commitHashLogIndexes, new MutableGraph());
    }



    private int getLogIndexOfCommit(@NotNull Hash commitHash) {
        Integer index = commitHashLogIndexes.get(commitHash);
        if (index == null) {
            return lastLogIndex + 1;
        } else {
            return index;
        }
    }



    private MutableNode addCurrentCommitAndFinishRow(@NotNull Hash commitHash) {
        MutableNode node = underdoneNodes.remove(commitHash);
        if (node == null) {
            node = new MutableNode(new Branch(commitHash), commitHash);
        }
        node.setType(COMMIT_NODE);
        node.setNodeRow(nextRow);

        nextRow.getInnerNodeList().add(node);
        graph.getAllRows().add(nextRow);
        nextRow = new MutableNodeRow(graph, nextRow.getRowIndex() + 1);
        return node;
    }

    private void addParent(MutableNode node, Hash parentHash, Branch branch) {
        MutableNode parentNode = underdoneNodes.remove(parentHash);
        if (parentNode == null) {
            parentNode = new MutableNode(branch, parentHash);
            createUsualEdge(node, parentNode, branch);
            underdoneNodes.put(parentHash, parentNode);
        } else {
            createUsualEdge(node, parentNode, branch);
            int parentRowIndex = getLogIndexOfCommit(parentHash);

            // i.e. we need of create EDGE_NODE node
            if (nextRow.getRowIndex() != parentRowIndex) {
                parentNode.setNodeRow(nextRow);
                parentNode.setType(EDGE_NODE);
                nextRow.getInnerNodeList().add(parentNode);

                MutableNode newParentNode = new MutableNode(parentNode.getBranch(), parentHash);
                createUsualEdge(parentNode, newParentNode, parentNode.getBranch());
                underdoneNodes.put(parentHash, newParentNode);
            } else {
                // i.e. node must be added in nextRow, when addCurrentCommitAndFinishRow() will called in next time
                underdoneNodes.put(parentHash, parentNode);
            }
        }
    }

    private void append(@NotNull Commit commit) {
        MutableNode node = addCurrentCommitAndFinishRow(commit.getCommitHash());

        List<Hash> parents = commit.getParentHashes();
        if (parents.size() == 1) {
            addParent(node, parents.get(0), node.getBranch());
        } else {
            for (Hash parentHash : parents) {
                addParent(node, parentHash, new Branch(node.getCommitHash(), parentHash));
            }
        }
    }


    private void lastActions() {
        Set<Hash> notReadiedCommitHashes = underdoneNodes.keySet();
        for (Hash hash : notReadiedCommitHashes) {
            MutableNode underdoneNode = underdoneNodes.get(hash);
            underdoneNode.setNodeRow(nextRow);
            underdoneNode.setType(END_COMMIT_NODE);
            nextRow.getInnerNodeList().add(underdoneNode);
        }
        if (!nextRow.getInnerNodeList().isEmpty()) {
            graph.getAllRows().add(nextRow);
        }
    }

    // local package
    @NotNull
    MutableGraph runBuild(@NotNull List<Commit> commits) {
        if (commits.size() == 0) {
            throw new IllegalArgumentException("Empty list commits");
        }
        for (Commit commit : commits) {
            append(commit);
        }
        lastActions();
        graph.updateVisibleRows();
        return graph;
    }

}
