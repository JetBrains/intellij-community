package org.hanuna.gitalk.graph.mutable_graph;

import org.hanuna.gitalk.graph.graph_elements.Branch;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.graph.mutable_graph.graph_elements_impl.AbstractMutableGraphElement;
import org.hanuna.gitalk.graph.mutable_graph.graph_elements_impl.MutableNode;
import org.hanuna.gitalk.graph.mutable_graph.graph_elements_impl.SimpleEdge;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class MutableGraphUtils {

    @NotNull
    public static MutableNode assertCast(@NotNull Node node) {
        assert node.getClass() == MutableNode.class;
        return (MutableNode) node;
    }

    public static void removeEdge(@NotNull Edge edge) {
        MutableNode upNode = assertCast(edge.getUpNode());
        MutableNode downNode = assertCast(edge.getDownNode());
        upNode.removeDownEdge(edge);
        downNode.removeUpEdge(edge);
    }

    public static void createEdge(@NotNull Node up, @NotNull Node down, @NotNull Edge.Type type, @NotNull Branch branch) {
        MutableNode upNode = assertCast(up);
        MutableNode downNode =  assertCast(down);
        Edge edge = new SimpleEdge(upNode, downNode, type, branch);
        upNode.addDownEdge(edge);
        downNode.addUpEdge(edge);
    }

    public static void setVisible(@NotNull Node node, boolean visible) {
        MutableNode mutableNode = assertCast(node);
        mutableNode.setVisible(visible);
    }

    public static void setSelected(@NotNull GraphElement graphElement, boolean selected) {
        if (graphElement instanceof AbstractMutableGraphElement) {
            ((AbstractMutableGraphElement) graphElement).setSelected(selected);
        } else {
            throw new IllegalStateException("unexpected graph elements");
        }
    }

}
