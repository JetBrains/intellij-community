package org.hanuna.gitalk.graph.mutable.elements;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.OneElementList;
import org.hanuna.gitalk.graph.elements.Branch;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public final class MutableNode implements Node {
    private final Commit commit;
    private final Branch branch;
    private Type type = null;
    private MutableNodeRow row = null;
    private boolean visible = true;

    private final List<Edge> upEdges = new OneElementList<Edge>();
    private final List<Edge> downEdges = new OneElementList<Edge>();


    public MutableNode(@NotNull Commit commit, @NotNull Branch branch) {
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
    public List<Edge> getUpEdges() {
        return Collections.unmodifiableList(upEdges);
    }

    @NotNull
    @Override
    public List<Edge> getDownEdges() {
        return Collections.unmodifiableList(downEdges);
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

    @Override
    public Node getNode() {
        return this;
    }

    @Override
    public Edge getEdge() {
        return null;
    }

}
