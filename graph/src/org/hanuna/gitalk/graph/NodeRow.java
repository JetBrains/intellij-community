package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface NodeRow {

    @NotNull
    public ReadOnlyList<Node> getNodes();

    public int getLogIndex();
}
