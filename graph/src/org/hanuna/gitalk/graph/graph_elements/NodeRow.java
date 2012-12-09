package org.hanuna.gitalk.graph.graph_elements;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface NodeRow {

    @NotNull
    public ReadOnlyList<Node> getVisibleNodes();

    public int getRowIndex();
}
