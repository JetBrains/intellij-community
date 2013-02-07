package org.hanuna.gitalk.graphmodel.impl;

import org.hanuna.gitalk.common.Get;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.mutable.MutableGraph;
import org.hanuna.gitalk.graph.mutable.elements.MutableNode;
import org.hanuna.gitalk.graph.mutable.elements.MutableNodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author erokhins
 */
public class BranchVisibleNodes {
    private final MutableGraph graph;
    private final Set<Node> visibleNodes = new HashSet<Node>();

    public BranchVisibleNodes(MutableGraph graph) {
        this.graph = graph;
    }

    public void setVisibleBranchesNodes(@NotNull Get<Node, Boolean> isStartedNode) {
        visibleNodes.clear();
        for (MutableNodeRow row : graph.getAllRows()) {
            for (MutableNode node : row.getInnerNodeList()) {
                if (isStartedNode.get(node) || visibleNodes.contains(node)) {
                    for (Edge edge : node.getInnerDownEdges()) {
                        visibleNodes.add(edge.getDownNode());
                    }
                }
            }
        }
    }

    public Set<Node> getVisibleNodes() {
        return Collections.unmodifiableSet(visibleNodes);
    }
}
