// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.util.graph.InboundSemiGraph;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

final class CachingSemiGraph<Node> implements InboundSemiGraph<Node> {
  private final List<Node> nodes;
  private final Map<Node, List<Node>> in;

  CachingSemiGraph(@NotNull List<Node> nodes, @NotNull Function<Node, List<Node>> inSupplier) {
    this.nodes = nodes;
    in = new HashMap<>(nodes.size());
    for (Node node : nodes) {
      List<Node> list = inSupplier.apply(node);
      if (!list.isEmpty()) {
        in.put(node, list);
      }
    }
  }

  @NotNull
  @Override
  public Collection<Node> getNodes() {
    return nodes;
  }

  @NotNull
  @Override
  public Iterator<Node> getIn(@NotNull Node n) {
    List<Node> inNodes = in.get(n);
    return inNodes == null ? Collections.emptyIterator() : inNodes.iterator();
  }

  @NotNull
  public List<Node> getInList(@NotNull Node n) {
    List<Node> inNodes = in.get(n);
    return inNodes == null ? Collections.emptyList() : inNodes;
  }
}