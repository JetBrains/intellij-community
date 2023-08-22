// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class GraphImpl implements Graph {

  private final BackDependencyIndex myDependencyIndex; // nodeId -> nodes, referencing the nodeId
  private final List<BackDependencyIndex> myIndices = new ArrayList<>();
  protected final MultiMaplet<ReferenceID, NodeSource> myNodeToSourcesMap;
  protected final MultiMaplet<NodeSource, Node<?, ?>> mySourceToNodesMap;

  protected GraphImpl(@NotNull MapletFactory cFactory) {
    addIndex(myDependencyIndex = new NodeDependenciesIndex(cFactory));
    myNodeToSourcesMap = cFactory.createSetMultiMaplet();
    mySourceToNodesMap = cFactory.createSetMultiMaplet();
  }

  // todo: ensure both dependency-graph and delta always have the same set of back-deps indices
  protected final void addIndex(BackDependencyIndex index) {
    myIndices.add(index);
  }

  /**
   * Obtain a list of backward dependencies for a certain node, denoted by a ReferenceID
   * @param id - a ReferenceID of one or more nodes
   * @return all known ids of Nodes that depend on nodes with the given id
   */
  protected @NotNull Iterable<ReferenceID> getDependingNodes(@NotNull ReferenceID id) {
    return myDependencyIndex.getDependencies(id);
  }

  @Override
  public final Iterable<BackDependencyIndex> getIndices() {
    return myIndices;
  }

  @Override
  @Nullable
  public final BackDependencyIndex getIndex(String name) {
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
  public Iterable<NodeSource> getSources() {
    return mySourceToNodesMap.getKeys();
  }

  @Override
  public Iterable<Node<?, ?>> getNodes(@NotNull NodeSource source) {
    var nodes = mySourceToNodesMap.get(source);
    return nodes != null? nodes : Collections.emptyList();
  }

}
