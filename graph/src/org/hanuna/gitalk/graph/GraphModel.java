package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface GraphModel {

    @NotNull
    public ReadOnlyList<NodeRow> getNodeRows();

    /**
     * @param edge .getType() == hideBranch or throw IllegalStateException
     */
    public void showBranch(@NotNull Edge edge);

    /**
     * from upNode to downNode will be simple way 1:1 (1 parent & 1 children)
     * after operation will be Edge(upNode, downNode, hideBranch, branchIndex),
     * where branchIndex will be equal branchIndex all intermediate edges
     */
    public void hideBranch(@NotNull Node upNode, @NotNull Node downNode);

}
