package org.hanuna.gitalk.graphmodel;

import org.hanuna.gitalk.common.Get;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
public interface FragmentManager {

    @Nullable
    public GraphFragment relateFragment(@NotNull GraphElement graphElement);

    @NotNull
    public Replace show(@NotNull GraphFragment fragment);

    @NotNull
    public Replace hide(@NotNull GraphFragment fragment);

    //true, if node is unhiddenNode
    public void setUnhiddenNodes(@NotNull Get<Node, Boolean> unhiddenNodes);

    void hideAll();
}
