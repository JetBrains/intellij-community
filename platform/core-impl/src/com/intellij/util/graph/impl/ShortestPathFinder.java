// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.graph.impl;

import com.intellij.util.graph.Graph;
import com.intellij.util.graph.InboundSemiGraph;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
public class ShortestPathFinder<Node> {
  private final InboundSemiGraph<Node> myGraph;

  public ShortestPathFinder(Graph<Node> graph) {
    myGraph = graph;
  }

  public ShortestPathFinder(InboundSemiGraph<Node> graph) {
    myGraph = graph;
  }

  public @Nullable List<Node> findPath(Node start, Node finish) {
    Map<Node, Node> nextNodes = new HashMap<>();
    Deque<Node> queue = new ArrayDeque<>();
    queue.addLast(finish);

    boolean found = false;
    while (!queue.isEmpty()) {
      final Node node = queue.removeFirst();
      if (node.equals(start)) {
        found = true;
        break;
      }

      final Iterator<Node> in = myGraph.getIn(node);
      while (in.hasNext()) {
        Node prev = in.next();
        if (!nextNodes.containsKey(prev)) {
          nextNodes.put(prev, node);
          queue.addLast(prev);
        }
      }
    }

    if (!found) {
      return null;
    }

    List<Node> path = new ArrayList<>();
    Node current = start;
    while (!current.equals(finish)) {
      path.add(current);
      current = nextNodes.get(current);
    }
    path.add(finish);
    return path;
  }
}