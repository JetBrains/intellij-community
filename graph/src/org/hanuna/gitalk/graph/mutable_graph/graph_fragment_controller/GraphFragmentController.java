package org.hanuna.gitalk.graph.mutable_graph.graph_fragment_controller;

import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.GraphFragment;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
public interface GraphFragmentController {

    @Nullable
    public GraphFragment relateFragment(@NotNull GraphElement graphElement);

    @NotNull
    public Replace hideFragment(@NotNull GraphFragment fragment);

    @NotNull
    public Replace showFragment(@NotNull GraphFragment fragment);

    public boolean isHidden(@NotNull GraphFragment fragment);
}
