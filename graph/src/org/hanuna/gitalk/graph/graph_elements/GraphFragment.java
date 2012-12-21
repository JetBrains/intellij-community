package org.hanuna.gitalk.graph.graph_elements;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface GraphFragment {

    @NotNull
    public Node getUpNode();

    @NotNull
    public Node getDownNode();

    public void intermediateWalker(@NotNull GraphElementRunnable graphRunnable);

    public static interface GraphElementRunnable {
        public void edgeRun(@NotNull Edge edge);
        public void nodeRun(@NotNull Node node);
    }

}
