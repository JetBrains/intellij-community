package org.hanuna.gitalk.graph.elements;

import org.hanuna.gitalk.commit.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface Node extends GraphElement {

    public int getRowIndex();

    @NotNull
    public NodeType getType();

    @NotNull
    public List<Edge> getUpEdges();

    @NotNull
    public List<Edge> getDownEdges();

    /**
     *
     * @return if type == COMMIT_NODE - this commit.
     * if type == EDGE_NODE - common Parent
     * if type == END_COMMIT_NODE - parent of This Commit
     */
    @NotNull
    public Hash getCommitHash();

    public static enum NodeType {
        COMMIT_NODE,
        EDGE_NODE,
        END_COMMIT_NODE
    }

}
