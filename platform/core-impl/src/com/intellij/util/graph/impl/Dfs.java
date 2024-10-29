// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.graph.impl;

import com.intellij.util.graph.OutboundSemiGraph;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;


@ApiStatus.Internal
public final class Dfs {

  private Dfs() { }

  public static <Node> void performDfs(
    @NotNull OutboundSemiGraph<Node> graph,
    @NotNull Node root,
    @NotNull Consumer<? super Node> visitor
  ) {
    final Set<Node> visited = new HashSet<>();
    final Deque<Node> stack = new ArrayDeque<>();
    stack.push(root);

    while (!stack.isEmpty()) {
      final Node curr = stack.pop();

      if (!visited.contains(curr)) {
        visitor.accept(curr);
        visited.add(curr);
      }

      graph.getOut(curr).forEachRemaining(next -> {
        if (!visited.contains(next)) {
          stack.push(next);
        }
      });
    }
  }
}
