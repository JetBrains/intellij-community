package org.hanuna.gitalk.graphmodel.impl;

import com.intellij.util.Function;
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
  private Set<Node> visibleNodes = Collections.emptySet();

  public BranchVisibleNodes(MutableGraph graph) {
    this.graph = graph;
  }

  public Set<Node> generateVisibleBranchesNodes(@NotNull Function<Node, Boolean> isStartedNode) {
    Set<Node> visibleNodes = new HashSet<Node>();
    for (MutableNodeRow row : graph.getAllRows()) {
      for (MutableNode node : row.getInnerNodeList()) {
        if (isStartedNode.fun(node)) {
          visibleNodes.add(node);
        }
        if (isStartedNode.fun(node) || visibleNodes.contains(node)) {
          for (Edge edge : node.getInnerDownEdges()) {
            visibleNodes.add(edge.getDownNode());
          }
        }
      }
    }
    return visibleNodes;
  }

  public void setVisibleNodes(@NotNull Set<Node> visibleNodes) {
    this.visibleNodes = visibleNodes;
  }

  public Set<Node> getVisibleNodes() {
    return Collections.unmodifiableSet(visibleNodes);
  }

  public boolean isVisibleNode(@NotNull Node node) {
    return visibleNodes.contains(node);
  }

}
