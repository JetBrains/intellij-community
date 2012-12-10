package org.hanuna.gitalk.graph.graph_elements;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface Edge extends GraphElement {

    @NotNull
    public Node getUpNode();

    @NotNull
    public Node getDownNode();

    @NotNull
    public Type getType();

    public static enum Type{
        USUAL,
        HIDE_FRAGMENT
    }
}
