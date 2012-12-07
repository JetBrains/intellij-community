package org.hanuna.gitalk.graph.mutable_graph.graph_elements_impl;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.graph_elements.Branch;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public final class MutableNode extends AbstractMutableGraphElement implements Node {
    private final Commit commit;
    private Type type = null;
    private MutableNodeRow row = null;
    private boolean visible = true;

    private final List<Edge> upEdges = new ArrayList<Edge>(2);
    private final List<Edge> downEdges = new ArrayList<Edge>(2);


    public MutableNode(@NotNull Commit commit, @NotNull Branch branch) {
        super(branch);
        this.commit = commit;
        this.branch = branch;
    }

    public void setType(@NotNull Type type) {
        this.type = type;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
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

    @Override
    public Node getNode() {
        return this;
    }

    @Override
    public Edge getEdge() {
        return null;
    }

}
