package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface GraphFragment {

    @NotNull
    public Node getUpNode();

    @NotNull
    public Node getDownNode();

    public void intermediateWalker(@NotNull Runner runner);

    public static interface Runner {

        public void edgeRun(@NotNull Edge edge);

        public void nodeRun(@NotNull Node node);

    }

}
