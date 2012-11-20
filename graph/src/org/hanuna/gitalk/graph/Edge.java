package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.graph.select.AbstractSelect;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class Edge extends AbstractSelect {
    private final Node upNode;
    private final Node downNode;
    private final Type type;
    private final Branch branch;

    public Edge(@NotNull Node upNode, @NotNull Node downNode, @NotNull Type type, Branch branch) {
        this.upNode = upNode;
        this.downNode = downNode;
        this.type = type;
        this.branch = branch;
    }

    public Node getUpNode() {
        return upNode;
    }

    public Node getDownNode() {
        return downNode;
    }

    public Type getType() {
        return type;
    }

    public Branch getBranch() {
        return branch;
    }

    public static enum Type{
        usual,
        hideBranch
    }
}
