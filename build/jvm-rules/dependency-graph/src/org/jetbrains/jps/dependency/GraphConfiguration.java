// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

public interface GraphConfiguration {

  @NotNull
  NodeSourcePathMapper getPathMapper();

  @NotNull
  DependencyGraph getGraph();

  static GraphConfiguration create(@NotNull DependencyGraph graph, @NotNull NodeSourcePathMapper pathMapper) {
    return new GraphConfiguration() {
      @Override
      public @NotNull NodeSourcePathMapper getPathMapper() {
        return pathMapper;
      }

      @Override
      public @NotNull DependencyGraph getGraph() {
        return graph;
      }
    };
  }
}
