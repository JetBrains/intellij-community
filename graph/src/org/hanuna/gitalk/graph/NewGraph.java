package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface NewGraph {

    @NotNull
    public List<NodeRow> getNodeRows();




}
