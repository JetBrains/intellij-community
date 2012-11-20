package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
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
    private final Commit commit;
    private final Branch branch;
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

    public void addUpEdge(@NotNull Edge edge) {
        upEdges.add(edge);
    }

    public void addDownEdge(@NotNull Edge edge) {
        downEdges.add(edge);
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
