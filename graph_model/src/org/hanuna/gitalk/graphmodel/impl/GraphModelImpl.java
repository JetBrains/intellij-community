package org.hanuna.gitalk.graphmodel.impl;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.mutable.GraphBuilder;
import org.hanuna.gitalk.graph.mutable.MutableGraph;
import org.hanuna.gitalk.graphmodel.FragmentManager;
import org.hanuna.gitalk.graphmodel.GraphModel;
import org.hanuna.gitalk.graphmodel.fragment.FragmentManagerImpl;
import org.hanuna.gitalk.log.commit.Commit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author erokhins
 */
public class GraphModelImpl implements GraphModel {
    private final MutableGraph graph;
    private final FragmentManagerImpl fragmentManager;
    private final BranchVisibleNodes visibleNodes;
    private final List<Executor<Replace>> listeners = new ArrayList<Executor<Replace>>();

    private Set<Node> branchVisibilityStartedNodes = Collections.emptySet();

    public GraphModelImpl(MutableGraph graph) {
        this.graph = graph;
        this.fragmentManager = new FragmentManagerImpl(graph, new FragmentManagerImpl.CallBackFunction() {
            @Override
            public Replace runIntermediateUpdate(@NotNull Node upNode, @NotNull Node downNode) {
                return GraphModelImpl.this.updateIntermediate(upNode, downNode);
            }

            @Override
            public void fullUpdate() {
                GraphModelImpl.this.fullUpdate();
            }
        });
        this.visibleNodes = new BranchVisibleNodes(graph);
    }

    @NotNull
    private Replace updateIntermediate(@NotNull Node upNode, @NotNull Node downNode) {
        int upRowIndex = upNode.getRowIndex();
        int downRowIndex = downNode.getRowIndex();
        graph.updateVisibleRows();

        Replace replace = Replace.buildFromToInterval(upRowIndex, downRowIndex, upNode.getRowIndex(), downNode.getRowIndex());
        callUpdateListener(replace);
        return replace;
    }

    private void fullUpdate() {
        int oldSize = graph.getNodeRows().size();
        graph.updateVisibleRows();
        Replace replace = Replace.buildFromToInterval(0, oldSize - 1, 0, graph.getNodeRows().size() - 1);
        callUpdateListener(replace);
    }

    @NotNull
    @Override
    public Graph getGraph() {
        return graph;
    }

    @Override
    public void appendCommitsToGraph(@NotNull List<Commit> commits) {
        GraphBuilder.addCommitsToGraph(graph, commits);
        visibleNodes.setVisibleBranchesNodes(branchVisibilityStartedNodes);
        fullUpdate();
    }

    @Override
    public void setVisibleBranchesNodes(@NotNull Set<Node> startedNodes) {
        this.branchVisibilityStartedNodes = startedNodes;
        visibleNodes.setVisibleBranchesNodes(startedNodes);
        fullUpdate();
    }

    @NotNull
    @Override
    public FragmentManager getFragmentManager() {
        return fragmentManager;
    }

    private void callUpdateListener(@NotNull Replace replace) {
        for (Executor<Replace> listener : listeners) {
            listener.execute(replace);
        }
    }

    @Override
    public void addUpdateListener(@NotNull Executor<Replace> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeAllListeners() {
        listeners.clear();
    }
}
