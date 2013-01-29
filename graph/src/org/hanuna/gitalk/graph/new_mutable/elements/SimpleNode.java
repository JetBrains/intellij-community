package org.hanuna.gitalk.graph.new_mutable.elements;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.graph.elements.Branch;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.new_mutable.EdgeController;
import org.hanuna.gitalk.graph.new_mutable.ElementVisibilityController;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author erokhins
 */
public class SimpleNode implements Node {
    private final MutableNodeRow nodeRow;
    private final Branch branch;
    private final Commit commit;
    private final Type type;

    public SimpleNode(MutableNodeRow nodeRow, Branch branch, Commit commit, Type type) {
        this.nodeRow = nodeRow;
        this.branch = branch;
        this.commit = commit;
        this.type = type;
    }

    @Override
    public int getRowIndex() {
        return nodeRow.getRowIndex();
    }


    @NotNull
    @Override
    public Type getType() {
        return type;
    }

    private EdgeController getEdgeController() {
        return nodeRow.getMutableGraph().getEdgeController();
    }

    @NotNull
    private List<Edge> getVisibleEdges(@NotNull Collection<Edge> edges) {
        ElementVisibilityController visibilityController = nodeRow.getMutableGraph().getVisibilityController();
        List<Edge> visibleEdge = new ArrayList<Edge>();
        for (Edge edge : edges) {
            if (visibilityController.isVisible(edge)) {
                visibleEdge.add(edge);
            }
        }
        return visibleEdge;
    }

    @NotNull
    @Override
    public List<Edge> getUpEdges() {
        return getVisibleEdges(getEdgeController().getUpEdges(this));
    }

    @NotNull
    @Override
    public List<Edge> getDownEdges() {
        return getVisibleEdges(getEdgeController().getDownEdges(this));
    }

    @NotNull
    @Override
    public Commit getCommit() {
        return commit;
    }

    @NotNull
    @Override
    public Branch getBranch() {
        return branch;
    }

    @Override
    public Node getNode() {
        return this;
    }

    @Override
    public Edge getEdge() {
        return null;
    }
}
