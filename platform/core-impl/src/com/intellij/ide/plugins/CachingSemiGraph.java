// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.util.graph.InboundSemiGraph;
import org.jetbrains.annotations.NotNull;

import java.util.*;

final class CachingSemiGraph<Node> implements InboundSemiGraph<Node> {
  private final Collection<Node> nodes;
  private final Map<Node, List<Node>> in;

  CachingSemiGraph(@NotNull Collection<Node> nodes, @NotNull Map<Node, List<Node>> in) {
    this.nodes = nodes;
    this.in = in;
  }

  @Override
  public @NotNull Collection<Node> getNodes() {
    return nodes;
  }

  @Override
  public @NotNull Iterator<Node> getIn(@NotNull Node n) {
    List<Node> inNodes = in.get(n);
    return inNodes == null ? Collections.emptyIterator() : inNodes.iterator();
  }

  public @NotNull List<Node> getInList(@NotNull Node n) {
    List<Node> inNodes = in.get(n);
    return inNodes == null ? Collections.emptyList() : inNodes;
  }
}