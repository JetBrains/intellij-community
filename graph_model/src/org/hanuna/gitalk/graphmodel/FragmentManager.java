package org.hanuna.gitalk.graphmodel;

import org.hanuna.gitalk.common.Function;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.mutable.GraphDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
public interface FragmentManager {

    @Nullable
    public GraphFragment relateFragment(@NotNull GraphElement graphElement);

    @NotNull
    public UpdateRequest changeVisibility(@NotNull GraphFragment fragment);

    //true, if node is unhiddenNode
    public void setUnconcealedNodeFunction(@NotNull Function<Node, Boolean> isUnconcealedNode);

    void hideAll();

    void showAll();

    @NotNull
    public GraphDecorator getGraphDecorator();
}
