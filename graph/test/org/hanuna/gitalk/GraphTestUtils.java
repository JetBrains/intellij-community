package org.hanuna.gitalk;

import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class GraphTestUtils {


    @NotNull
    public static Node getCommitNode(Graph graph, int rowIndex) {
        NodeRow row = graph.getNodeRows().get(rowIndex);
        for (Node node : row.getNodes()) {
            if (node.getType() == Node.Type.COMMIT_NODE) {
                return node;
            }
        }
        throw new IllegalArgumentException();
    }
}
