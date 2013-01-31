package org.hanuna.gitalk.graph.new_mutable.elements;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.OneElementList;
import org.hanuna.gitalk.graph.elements.Branch;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.new_mutable.ElementVisibilityController;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableNode implements Node {
    private final MutableNodeRow nodeRow;
    private final Branch branch;
    private final Commit commit;
    private final Type type;

    private final List<Edge> upEdges = new OneElementList<Edge>();
    private final List<Edge> downEdges = new OneElementList<Edge>();

    public MutableNode(MutableNodeRow nodeRow, Branch branch, Commit commit, Type type) {
        this.nodeRow = nodeRow;
        this.branch = branch;
        this.commit = commit;
        this.type = type;
    }

    @NotNull
    public List<Edge> getInnerUpEdges() {
        return upEdges;
    }

    @NotNull
    public List<Edge> getInnerDownEdges() {
        return downEdges;
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
        return getVisibleEdges(upEdges);
    }

    @NotNull
    @Override
    public List<Edge> getDownEdges() {
        return getVisibleEdges(downEdges);
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
