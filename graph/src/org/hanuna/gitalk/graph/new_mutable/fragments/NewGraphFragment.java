package org.hanuna.gitalk.graph.new_mutable.fragments;

import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author erokhins
 */
public interface NewGraphFragment {

    public boolean isVisible();

    public void setVisibility(boolean visibility);

    @NotNull
    public Node getUpNode();

    @NotNull
    public Node getDownNode();

    @NotNull
    public Collection<Node> getIntermediateNodes();
}
