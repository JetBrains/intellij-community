package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface NewGraph {

    public int size();

    @NotNull
    public NodeRow getNodeRow(int rowIndex);


    public void addUpdateListener(@NotNull Executor<Replace> listener);
    public void removeAllListeners();

}
