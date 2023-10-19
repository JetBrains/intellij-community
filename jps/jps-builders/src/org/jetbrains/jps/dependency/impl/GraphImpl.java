// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class GraphImpl implements Graph {

  private final BackDependencyIndex myDependencyIndex; // nodeId -> nodes, referencing the nodeId
  private final List<BackDependencyIndex> myIndices = new ArrayList<>();
  protected final MultiMaplet<ReferenceID, NodeSource> myNodeToSourcesMap;
  protected final MultiMaplet<NodeSource, Node<?, ?>> mySourceToNodesMap;
  private final MapletFactory myContainerFactory;

  protected GraphImpl(@NotNull MapletFactory cFactory) {
    myContainerFactory = cFactory;
    addIndex(myDependencyIndex = new NodeDependenciesIndex(cFactory));
    myNodeToSourcesMap = cFactory.createSetMultiMaplet("node-sources-map");
    mySourceToNodesMap = cFactory.createSetMultiMaplet("source-nodes-map");
  }

  // todo: ensure both dependency-graph and delta always have the same set of back-deps indices
  protected final void addIndex(BackDependencyIndex index) {
    myIndices.add(index);
  }

  @Override
  public @NotNull Iterable<ReferenceID> getDependingNodes(@NotNull ReferenceID id) {
    return myDependencyIndex.getDependencies(id);
  }

  @Override
  public final Iterable<BackDependencyIndex> getIndices() {
    return myIndices;
  }

  @Override
  public final @Nullable BackDependencyIndex getIndex(String name) {
    for (BackDependencyIndex index : myIndices) {
      if (index.getName().equals(name)) {
        return index;
      }
    }
    return null;
  }

  @Override
  public Iterable<NodeSource> getSources(@NotNull ReferenceID id) {
    Iterable<NodeSource> nodeSources = myNodeToSourcesMap.get(id);
    return nodeSources != null? nodeSources : Collections.emptyList();
  }

  @Override
  public Iterable<ReferenceID> getRegisteredNodes() {
    return myNodeToSourcesMap.getKeys();
  }

  @Override
  public Iterable<NodeSource> getSources() {
    return mySourceToNodesMap.getKeys();
  }

  @Override
  public Iterable<Node<?, ?>> getNodes(@NotNull NodeSource source) {
    var nodes = mySourceToNodesMap.get(source);
    return nodes != null? nodes : Collections.emptyList();
  }

  public void close() throws IOException {
    if (myContainerFactory instanceof Closeable)  {
      ((Closeable)myContainerFactory).close();
    }
  }

}
