package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.graph.Node;
import org.hanuna.gitalk.graph.select.AbstractSelect;
import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.graph.Branch;
import org.hanuna.gitalk.graph.Edge;
import org.hanuna.gitalk.graph.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableNode extends AbstractSelect implements Node {
    private final Commit commit;
    private Type type = null;
    private NodeRow row = null;
    private int logIndex;
    private final Branch branch;

    private final List<Edge> upEdges = new ArrayList<Edge>(2);
    private final ReadOnlyList<Edge> readOnlyUpEdges = ReadOnlyList.newReadOnlyList(upEdges);

    private final List<Edge> downEdges = new ArrayList<Edge>(2);
    private final ReadOnlyList<Edge> readOnlyDownEdges = ReadOnlyList.newReadOnlyList(downEdges);


    public MutableNode(@NotNull Commit commit, @NotNull Branch branch) {
        this.commit = commit;
        this.branch = branch;
    }

    public void setType(@NotNull Type type) {
        this.type = type;
    }

    public void setLogIndex(int logIndex) {
        this.logIndex = logIndex;
    }

    public void setRow(@NotNull NodeRow row) {
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

    @NotNull
    @Override
    public NodeRow getRow() {
        return row;
    }

    @NotNull
    @Override
    public ReadOnlyList<Edge> getUpEdges() {
        return readOnlyUpEdges;
    }

    @NotNull
    @Override
    public ReadOnlyList<Edge> getDownEdges() {
        return readOnlyDownEdges;
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
