package org.hanuna.gitalk.graphmodel;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.Get;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.log.commit.Commit;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface GraphModel {

    @NotNull
    public Graph getGraph();

    public void appendCommitsToGraph(@NotNull List<Commit> commits);

    public void setVisibleBranchesNodes(@NotNull Get<Node, Boolean> isStartedNode);

    @NotNull
    public FragmentManager getFragmentManager();

    public void addUpdateListener(@NotNull Executor<Replace> listener);

    public void removeAllListeners();
}
