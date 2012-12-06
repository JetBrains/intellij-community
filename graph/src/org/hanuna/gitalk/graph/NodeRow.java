package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface NodeRow {

    @NotNull
    public ReadOnlyList<Node> getNodes();

    public int getRowIndex();
}
