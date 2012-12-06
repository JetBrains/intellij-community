package org.hanuna.gitalk.graph.graph_elements;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface Node extends GraphElement {

    public int getRowIndex();

    @NotNull
    public Type getType();

    @NotNull
    public ReadOnlyList<Edge> getUpEdges();

    @NotNull
    public ReadOnlyList<Edge> getDownEdges();

    /**
     *
     * @return if type == COMMIT_NODE - this commit.
     * if type == EDGE_NODE - common Parent
     * if type == END_COMMIT_NODE - parent of This Commit
     */
    @NotNull
    public Commit getCommit();

    public static enum Type{
        COMMIT_NODE,
        EDGE_NODE,
        END_COMMIT_NODE
    }

}
