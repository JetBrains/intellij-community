package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
public class ElementVisibilityController {
    private final Set<Node> hiddenNodes = new HashSet<Node>();
    private final Collection<Commit> visibleCommits = new HashSet<Commit>();
    private boolean showAllBranchesFlag = true;

    public boolean isVisible(@NotNull GraphElement graphElement) {
        Node node = graphElement.getNode();
        if (node != null) {
            return isVisibleNode(node);
        } else {
            Edge edge = graphElement.getEdge();
            assert edge != null;
            return isVisible(edge.getUpNode()) && isVisible(edge.getDownNode());
        }
    }

    private boolean isVisibleNode(@NotNull Node node) {
        return !hiddenNodes.contains(node) && (showAllBranchesFlag || visibleCommits.contains(node.getCommit()));
    }


    public void hide(@NotNull Collection<Node> graphElements) {
        hiddenNodes.addAll(graphElements);
    }

    public void show(@NotNull Collection<Node> graphElements) {
        hiddenNodes.removeAll(graphElements);
    }

    public void setShowAllBranchesFlag(boolean showAllBranchesFlag) {
        this.showAllBranchesFlag = showAllBranchesFlag;
    }

    public void setVisibleCommits(@NotNull List<Commit> visibleCommits) {
        this.visibleCommits.clear();
        this.visibleCommits.addAll(visibleCommits);
        showAllBranchesFlag = false;
    }


}
