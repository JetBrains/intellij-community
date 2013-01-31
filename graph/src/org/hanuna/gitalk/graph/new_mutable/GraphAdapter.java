package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.GraphFragmentController;
import org.hanuna.gitalk.graph.NewGraph;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.GraphFragment;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class GraphAdapter implements Graph {
    private final NewGraph graph;
    private final List<NodeRow> nodeRows;
    private final GraphFragmentController graphFragmentController = new GraphFragmentController() {
        @Override
        public GraphFragment relateFragment(@NotNull GraphElement graphElement) {
            return null;
        }

        @NotNull
        @Override
        public Replace setVisible(@NotNull GraphFragment fragment, boolean visible) {
            return Replace.ID_REPLACE;
        }

        @Override
        public boolean isVisible(@NotNull GraphFragment fragment) {
            return false;
        }
    };

    public GraphAdapter(final NewGraph graph) {
        this.graph = graph;
        nodeRows = graph.getNodeRows();
    }

    @NotNull
    @Override
    public List<NodeRow> getNodeRows() {
        return nodeRows;
    }

    @NotNull
    @Override
    public GraphFragmentController getFragmentController() {
        return graphFragmentController;
    }

    @Override
    public void addUpdateListener(@NotNull final GraphUpdateListener updateListener) {
        graph.addUpdateListener(new Executor<Replace>() {
            @Override
            public void execute(Replace key) {
                updateListener.doReplace(key);
            }
        });
    }

    @Override
    public void removeAllListeners() {
        graph.removeAllListeners();
    }
}
