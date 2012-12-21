package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.graph_elements.GraphFragment;
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
    public Replace setVisible(@NotNull GraphFragment fragment, boolean visible);

    public boolean isVisible(@NotNull GraphFragment fragment);
}
