// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph.impl;

import com.intellij.util.graph.MutableNetwork;
import com.intellij.util.graph.NetworkBuilder;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public class NetworkBuilderImpl<N, E> extends NetworkBuilder<N, E> {

  protected NetworkBuilderImpl(boolean directed) {
    super(directed);
  }

  @Override
  public <N1 extends N, E1 extends E> MutableNetwork<N1, E1> build() {
    com.google.common.graph.NetworkBuilder<Object, Object> guavaBuilder =
      myIsDirected ? com.google.common.graph.NetworkBuilder.directed()
                   : com.google.common.graph.NetworkBuilder.undirected();
    com.google.common.graph.NetworkBuilder<N, ? super E> builder = guavaBuilder
      .allowsParallelEdges(myDoAllowParallelEdges)
      .allowsSelfLoops(myDoAllowSelfLoops)
      .edgeOrder(GraphAdapter.unwrapOrder(myEdgeOrder))
      .nodeOrder(GraphAdapter.unwrapOrder(myNodeOrder));
    if (myExpectedEdgeCount.isPresent()) builder.expectedEdgeCount(myExpectedEdgeCount.getAsInt());
    if (myExpectedNodeCount.isPresent()) builder.expectedNodeCount(myExpectedNodeCount.getAsInt());

    return GraphAdapter.wrapNetwork(builder.build());
  }
}
