package org.hanuna.gitalk.graph.mutable;

import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
public interface GraphDecorator {

    public boolean isVisibleNode(@NotNull Node node);

    @Nullable
    public Edge getHideFragmentDownEdge(@NotNull Node node);

    @Nullable
    public Edge getHideFragmentUpEdge(@NotNull Node node);

}
