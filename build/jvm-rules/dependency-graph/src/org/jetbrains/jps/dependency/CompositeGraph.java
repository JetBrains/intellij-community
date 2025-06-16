// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jetbrains.jps.util.Iterators.*;

/**
 * A readonly composite graph view on top of several graph parts
 */
public interface CompositeGraph extends Graph {

  static CompositeGraph create(Iterable<Graph> parts) {
    Map<String, List<BackDependencyIndex>> indexMap = new HashMap<>();
    for (BackDependencyIndex index : flat(map(parts, Graph::getIndices))) {
      indexMap.computeIfAbsent(index.getName(), n -> new ArrayList<>()).add(index);
    }
    return new CompositeGraph() {
      final Map<String, CompositeBackDependencyIndex> myIndices = new HashMap<>();
      @Override
      public Iterable<BackDependencyIndex> getIndices() {
        return map(indexMap.keySet(), this::getIndex);
      }

      @Override
      public @Nullable BackDependencyIndex getIndex(String name) {
        List<BackDependencyIndex> indices = indexMap.get(name);
        return indices != null? myIndices.computeIfAbsent(name, n -> CompositeBackDependencyIndex.create(n, indices)) : null;
      }

      @Override
      public @NotNull Iterable<ReferenceID> getDependingNodes(@NotNull ReferenceID id) {
        return unique(flat(map(parts, gr -> gr.getDependingNodes(id))));
      }

      @Override
      public Iterable<NodeSource> getSources(@NotNull ReferenceID id) {
        return unique(flat(map(parts, gr -> gr.getSources(id))));
      }

      @Override
      public Iterable<ReferenceID> getRegisteredNodes() {
        return unique(flat(map(parts, Graph::getRegisteredNodes)));
      }

      @Override
      public Iterable<NodeSource> getSources() {
        return unique(flat(map(parts, Graph::getSources)));
      }

      @Override
      public Iterable<Node<?, ?>> getNodes(@NotNull NodeSource source) {
        return unique(flat(map(parts, gr -> gr.getNodes(source))));
      }
    };
  }
}
