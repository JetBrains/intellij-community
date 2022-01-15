// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph.impl;

import com.intellij.util.graph.GraphFactory;
import com.intellij.util.graph.Network;
import com.intellij.util.graph.NetworkBuilder;
import org.jetbrains.annotations.NotNull;


public class GraphFactoryImpl extends GraphFactory {

  @Override
  public @NotNull NetworkBuilder<Object, Object> directedNetwork() {
    return new NetworkBuilderImpl<>(true);
  }

  @Override
  public @NotNull NetworkBuilder<Object, Object> undirectedNetwork() {
    return new NetworkBuilderImpl<>(false);
  }

  @Override
  public @NotNull <N, E> NetworkBuilder<N, E> from(@NotNull Network<N, E> network) {
    return new NetworkBuilderImpl<N, E>(network.isDirected())
      .allowsParallelEdges(network.allowsParallelEdges())
      .allowsSelfLoops(network.allowsSelfLoops())
      .nodeOrder(network.nodeOrder())
      .edgeOrder(network.edgeOrder());
  }
}
