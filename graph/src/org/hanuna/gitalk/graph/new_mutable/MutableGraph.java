package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.NewGraph;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class MutableGraph implements NewGraph {

    @NotNull
    public EdgeController getEdgeController() {
        return null;
    }

    @NotNull
    public ElementVisibilityController getVisibilityController() {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @NotNull
    @Override
    public NodeRow getNodeRow(int rowIndex) {
        return null;
    }

    @Override
    public void addUpdateListener(@NotNull Executor<Replace> listener) {

    }

    @Override
    public void removeAllListeners() {

    }
}
