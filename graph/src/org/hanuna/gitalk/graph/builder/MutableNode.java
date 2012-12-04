package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.Branch;
import org.hanuna.gitalk.graph.Edge;
import org.hanuna.gitalk.graph.Node;
import org.hanuna.gitalk.graph.select.AbstractSelect;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableNode extends AbstractSelect implements Node {
    public static void createEdge(MutableNode upNode, MutableNode downNode, Edge.Type type, Branch branch) {
        Edge edge = new Edge(upNode, downNode, type, branch);
        upNode.addDownEdge(edge);
        downNode.addUpEdge(edge);
    }

    private final Commit commit;
    private Branch branch;
    private Type type = null;
    private MutableNodeRow row = null;

    private final List<Edge> upEdges = new ArrayList<Edge>(2);
    private final List<Edge> downEdges = new ArrayList<Edge>(2);


    public MutableNode(@NotNull Commit commit, @NotNull Branch branch) {
        this.commit = commit;
        this.branch = branch;
    }

    public void setType(@NotNull Type type) {
        this.type = type;
    }


    public void setRow(@NotNull MutableNodeRow row) {
        this.row = row;
    }

    public void setBranch(@NotNull Branch branch) {
        this.branch = branch;
    }

    public void addUpEdge(@NotNull Edge edge) {
        upEdges.add(edge);
    }

    public void addDownEdge(@NotNull Edge edge) {
        downEdges.add(edge);
    }

    public void removeUpEdge(@NotNull Edge edge) {
        upEdges.remove(edge);
    }

    public void removeDownEdge(@NotNull Edge edge) {
        downEdges.remove(edge);
    }

    @NotNull
    public MutableNodeRow getRow() {
        return row;
    }

    @NotNull
    @Override
    public Type getType() {
        return type;
    }

    @Override
    public int getRowIndex() {
        return row.getRowIndex();
    }

    @NotNull
    @Override
    public ReadOnlyList<Edge> getUpEdges() {
        return ReadOnlyList.newReadOnlyList(upEdges);
    }

    @NotNull
    @Override
    public ReadOnlyList<Edge> getDownEdges() {
        return ReadOnlyList.newReadOnlyList(downEdges);
    }

    @NotNull
    @Override
    public Commit getCommit() {
        return commit;
    }

    @NotNull
    @Override
    public Branch getBranch() {
        return branch;
    }

}
