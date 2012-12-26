package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.graph_elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface Graph {

    @NotNull
    public List<NodeRow> getNodeRows();

    @NotNull
    public GraphFragmentController getFragmentController();

    public void addUpdateListener(@NotNull GraphUpdateListener updateListener);

    public void removeAllListeners();

    public interface GraphUpdateListener {
        public void doReplace(@NotNull Replace replace);
    }
}
