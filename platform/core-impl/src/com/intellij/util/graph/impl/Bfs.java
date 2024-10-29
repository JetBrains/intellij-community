// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.graph.impl;

import com.intellij.util.graph.OutboundSemiGraph;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;


@ApiStatus.Internal
public final class Bfs {

  private Bfs() { }

  public static <Node> void performBfs(
    @NotNull OutboundSemiGraph<Node> graph,
    @NotNull Node root,
    @NotNull BiConsumer<? super Node, ? super Integer> visitor
  ) {
    final Set<Node> visited = new HashSet<>();
    final Queue<WithIndex<Node>> queue = new ArrayDeque<>();
    queue.add(new WithIndex<>(root, 0));

    while (!queue.isEmpty()) {
      final WithIndex<Node> curr = queue.poll();

      if (!visited.contains(curr)) {
        visitor.accept(curr.node, curr.index);
        visited.add(curr.node);
      }

      graph.getOut(curr.node).forEachRemaining(next -> {
        if (!visited.contains(next)) {
          queue.add(new WithIndex<>(next, curr.index + 1));
        }
      });
    }
  }


  private static final class WithIndex<Node> {
    private final @NotNull Node node;
    private final int index;

    private WithIndex(@NotNull Node node, int index) {
      this.node = node;
      this.index = index;
    }
  }
}
