package org.hanuna.gitalk.graph.mutable_graph;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.graph_elements.Branch;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.graph.mutable_graph.graph_elements_impl.MutableNode;
import org.hanuna.gitalk.graph.mutable_graph.graph_elements_impl.MutableNodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static org.hanuna.gitalk.graph.mutable_graph.MutableGraphUtils.createEdge;

/**
 * @author erokhins
 */
public class GraphBuilder {
    public static Graph build(List<Commit> commits) {
        Map<Commit, Integer> logIndexMap = new HashMap<Commit, Integer>(commits.size());
        for (int i = 0; i < commits.size(); i++) {
            logIndexMap.put(commits.get(i), i);
        }
        GraphBuilder builder = new GraphBuilder(logIndexMap);
        return builder.runBuild(commits);
    }

    public GraphBuilder(@NotNull Map<Commit, Integer> logIndexMap) {
        this.logIndexMap = logIndexMap;
    }

    private int lastLogIndex;
    private Map<Hash, MutableNode> notAddedNodes = new HashMap<Hash, MutableNode>();
    private MutableNodeRow nextRow;
    private final List<MutableNodeRow> rows = new ArrayList<MutableNodeRow>();
    private final Map<Commit, Integer> logIndexMap;

    private int getLogIndexOfCommit(@NotNull Commit commit) {
        final CommitData data = commit.getData();
        if (data == null) {
            return lastLogIndex + 1;
        } else {
            return logIndexMap.get(commit);
        }
    }



    // return added node
    private MutableNode addCurrentCommitAndFinishRow(Commit commit) {
        MutableNode node = notAddedNodes.remove(commit.hash());
        if (node == null) {
            node = new MutableNode(commit, new Branch(commit));
        }
        node.setRow(nextRow);
        node.setType(Node.Type.COMMIT_NODE);
        nextRow.add(node);
        rows.add(nextRow);

        nextRow = new MutableNodeRow(rows.size());
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
            // i.e. we need create new Node (parent commit isn't situated in next row)
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
                // i.e. it was real node.
                // do nothing
            }
        }
    }

    private void append(@NotNull Commit commit) {
        MutableNode node = addCurrentCommitAndFinishRow(commit);

        CommitData data = commit.getData();
        if (data == null) {
            throw new IllegalStateException("commit was append, but commitData is null");
        }
        List<Commit> parents = data.getParents();
        for (int i = 0; i < parents.size(); i++) {
            Commit parent = parents.get(i);
            if (i == 0) {
                addParent(node, parent, node.getBranch());
            } else {
                addParent(node, parent, new Branch(parent));
            }
        }
    }

    private void prepare(int lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
        nextRow = new MutableNodeRow(0);
    }

    private void lastActions() {
        final Collection<MutableNode> lastNodes = notAddedNodes.values();
        for (MutableNode node : lastNodes) {
            node.setRow(nextRow);
            node.setType(Node.Type.END_COMMIT_NODE);
            nextRow.add(node);
        }
        if (lastNodes.size() > 0) {
            rows.add(nextRow);
        }
    }

    @NotNull
    private Graph runBuild(List<Commit> commits) {
        prepare(commits.size() - 1);
        for (Commit commit : commits) {
            append(commit);
        }
        lastActions();
        return new MutableGraph(rows);
    }



}
