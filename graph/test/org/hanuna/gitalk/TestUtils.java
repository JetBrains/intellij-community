package org.hanuna.gitalk;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.graph.Edge;
import org.hanuna.gitalk.graph.GraphModel;
import org.hanuna.gitalk.graph.Node;
import org.hanuna.gitalk.graph.NodeRow;

/**
 * @author erokhins
 */
public class TestUtils {


    public static String toShortStr(Edge edge) {
        StringBuilder s = new StringBuilder();
        s.append(edge.getUpNode().getCommit().hash().toStrHash()).append(":");
        s.append(edge.getDownNode().getCommit().hash().toStrHash()).append(":");
        s.append(edge.getType()).append(":");
        s.append(edge.getBranch().getNumberOfBranch());
        return s.toString();
    }
    public static String toShortStr(ReadOnlyList<Edge> edges) {
        StringBuilder s = new StringBuilder();
        if (edges.size() > 0) {
            s.append(toShortStr(edges.get(0)));
        }
        for (int i = 1; i < edges.size(); i++) {
            s.append(" ").append(toShortStr(edges.get(i)));
        }
        return s.toString();
    }

    public static String toStr(Node node) {
        StringBuilder s = new StringBuilder();
        s.append(node.getCommit().hash().toStrHash()).append("|-");
        s.append(toShortStr(node.getUpEdges())).append("|-");
        s.append(toShortStr(node.getDownEdges())).append("|-");
        s.append(node.getType()).append("|-");
        s.append(node.getBranch().getNumberOfBranch()).append("|-");
        s.append(node.getRowIndex());
        return s.toString();
    }
    public static String toStr(NodeRow row) {
        StringBuilder s = new StringBuilder();
        ReadOnlyList<Node> nodes = row.getNodes();
        if (nodes.size() > 0) {
            s.append(toStr(nodes.get(0)));
        }
        for (int i = 1; i < nodes.size(); i++) {
            s.append(" ").append(toStr(nodes.get(i)));
        }
        return s.toString();
    }

    public static String toStr(GraphModel graph) {
        StringBuilder s = new StringBuilder();
        final ReadOnlyList<NodeRow> rows = graph.getNodeRows();
        if (rows.size() > 0)  {
            s.append(toStr(rows.get(0)));
        }
        for (int i = 1; i < rows.size(); i++) {
            s.append("\n").append(toStr(rows.get(i)));
        }
        return s.toString();
    }
}
