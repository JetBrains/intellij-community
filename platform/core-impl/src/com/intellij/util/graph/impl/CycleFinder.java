// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph.impl;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.Graph;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author anna
 * @since Feb 13, 2005
 */
public class CycleFinder<Node> {
  private final Graph<Node> myGraph;

  public CycleFinder(Graph<Node> graph) {
    myGraph = graph;
  }

  @NotNull
  public Set<List<Node>> getNodeCycles(final Node node) {
    final Set<List<Node>> result = new HashSet<>();

    final Graph<Node> graphWithoutNode = new Graph<Node>() {
      @Override
      public Collection<Node> getNodes() {
        final Collection<Node> nodes = myGraph.getNodes();
        nodes.remove(node);
        return nodes;
      }

      @Override
      public Iterator<Node> getIn(final Node n) {
        final Set<Node> nodes = ContainerUtil.newHashSet(myGraph.getIn(n));
        nodes.remove(node);
        return nodes.iterator();
      }

      @Override
      public Iterator<Node> getOut(final Node n) {
        final Set<Node> nodes = ContainerUtil.newHashSet(myGraph.getOut(n));
        nodes.remove(node);
        return nodes.iterator();
      }
    };

    final Set<Node> inNodes = ContainerUtil.newHashSet(myGraph.getIn(node));
    final Set<Node> outNodes = ContainerUtil.newHashSet(myGraph.getOut(node));
    final Set<Node> retainNodes = new HashSet<>(inNodes);
    retainNodes.retainAll(outNodes);
    for (Node node1 : retainNodes) {
      result.add(ContainerUtil.newArrayList(node1, node));
    }
    inNodes.removeAll(retainNodes);
    outNodes.removeAll(retainNodes);

    ShortestPathFinder<Node> finder = new ShortestPathFinder<>(graphWithoutNode);
    for (Node fromNode : outNodes) {
      for (Node toNode : inNodes) {
        final List<Node> shortestPath = finder.findPath(fromNode, toNode);
        if (shortestPath != null) {
          List<Node> path = new ArrayList<>(shortestPath.size() + 1);
          path.addAll(shortestPath);
          path.add(node);
          result.add(path);
        }
      }
    }

    return result;
  }
}