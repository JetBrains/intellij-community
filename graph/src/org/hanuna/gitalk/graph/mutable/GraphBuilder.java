package org.hanuna.gitalk.graph.mutable;

import org.hanuna.gitalk.commitmodel.Commit;
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

    private final Map<Commit, MutableNode> underdoneNodes = new HashMap<Commit, MutableNode>();
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


    private void createUsualEdge(@NotNull MutableNode up, @NotNull MutableNode down, @NotNull Branch branch) {
        UsualEdge edge = new UsualEdge(up, down, branch);
        up.getInnerDownEdges().add(edge);
        down.getInnerUpEdges().add(edge);
    }


    private MutableNode addCurrentCommitAndFinishRow(@NotNull Commit commit) {
        MutableNode node = underdoneNodes.remove(commit);
        if (node == null) {
            node = new MutableNode(new Branch(commit), commit);
        }
        node.setType(COMMIT_NODE);
        node.setNodeRow(nextRow);

        nextRow.getInnerNodeList().add(node);
        graph.getAllRows().add(nextRow);
        nextRow = new MutableNodeRow(graph, nextRow.getRowIndex() + 1);
        return node;
    }

    private void addParent(MutableNode node, Commit parentCommit, Branch branch) {
        MutableNode parentNode = underdoneNodes.remove(parentCommit);
        if (parentNode == null) {
            parentNode = new MutableNode(branch, parentCommit);
            createUsualEdge(node, parentNode, branch);
            underdoneNodes.put(parentCommit, parentNode);
        } else {
            createUsualEdge(node, parentNode, branch);
            int parentRowIndex = getLogIndexOfCommit(parentCommit);

            // i.e. we need of create EDGE_NODE node
            if (nextRow.getRowIndex() != parentRowIndex) {
                parentNode.setNodeRow(nextRow);
                parentNode.setType(EDGE_NODE);
                nextRow.getInnerNodeList().add(parentNode);

                MutableNode newParentNode = new MutableNode(parentNode.getBranch(), parentCommit);
                createUsualEdge(parentNode, newParentNode, parentNode.getBranch());
                underdoneNodes.put(parentCommit, newParentNode);
            } else {
                // i.e. node must be added in nextRow, when addCurrentCommitAndFinishRow() will called in next time
                underdoneNodes.put(parentCommit, parentNode);
            }
        }
    }

    private void append(@NotNull Commit commit) {
        MutableNode node = addCurrentCommitAndFinishRow(commit);

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
        Set<Commit> notReadiedCommits = underdoneNodes.keySet();
        for (Commit commit : notReadiedCommits) {
            MutableNode underdoneNode = underdoneNodes.get(commit);
            underdoneNode.setNodeRow(nextRow);
            underdoneNode.setType(END_COMMIT_NODE);
            nextRow.getInnerNodeList().add(underdoneNode);
        }
        if (!nextRow.getInnerNodeList().isEmpty()) {
            graph.getAllRows().add(nextRow);
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
