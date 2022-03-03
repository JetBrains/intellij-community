// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph.impl;

import com.intellij.util.graph.*;
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
  public @NotNull <N, E> NetworkBuilder<N, E> newNetworkWithSameProperties(@NotNull Network<N, E> network) {
    return new NetworkBuilderImpl<N, E>(network.isDirected())
      .allowsParallelEdges(network.allowsParallelEdges())
      .allowsSelfLoops(network.allowsSelfLoops())
      .nodeOrder(network.nodeOrder())
      .edgeOrder(network.edgeOrder());
  }

  @Override
  public @NotNull <N> MutableNetwork<N, EndpointPair<N>> toNetwork(@NotNull Graph<N> graph) {
    MutableNetwork<N, EndpointPair<N>> network = new NetworkBuilderImpl<N, EndpointPair<N>>(true)
      .allowsParallelEdges(true)
      .allowsSelfLoops(true)
      .nodeOrder(ElementOrder.unordered())
      .edgeOrder(ElementOrder.unordered())
      .build();

    for (N node : graph.getNodes()) {
      network.addNode(node);
      graph.getIn(node).forEachRemaining(prev -> {
        network.addEdge(prev, node, EndpointPair.ordered(prev, node));
      });
      graph.getOut(node).forEachRemaining(next -> {
        network.addEdge(node, next, EndpointPair.ordered(node, next));
      });
    }

    return network;
  }
}
