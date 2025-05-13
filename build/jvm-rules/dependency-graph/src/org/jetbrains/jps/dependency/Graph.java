// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.javac.Iterators;

public interface Graph {
  Iterable<BackDependencyIndex> getIndices();

  @Nullable
  BackDependencyIndex getIndex(String name);

  /**
   * Obtain a list of backward dependencies for a certain node, denoted by a ReferenceID
   * @param id - a ReferenceID of one or more nodes
   * @return all known ids of Nodes that depend on nodes with the given id
   */
  @NotNull Iterable<ReferenceID> getDependingNodes(@NotNull ReferenceID id);

  /**
   * A mapping from nodes to sources used to produce those nodes
   *
   * @param id a node identifier
   * @return all sources used to produce nodes with the given id.
   * Note that there may be several nodes with the same ReferenceID
   */
  Iterable<NodeSource> getSources(@NotNull ReferenceID id);

  Iterable<ReferenceID> getRegisteredNodes();

  /**
   * @return all sources registered in this graph
   */
  Iterable<NodeSource> getSources();

  /**
   * A mapping from sources to nodes, produced from these sources
   *
   * @param source a representation of particular node source
   * @return all nodes produced from the given source
   */
  Iterable<Node<?, ?>> getNodes(@NotNull NodeSource source);

  default <T extends Node<T, ?>> Iterable<T> getNodes(NodeSource src, Class<T> nodeSelector) {
    return getNodesOfType(getNodes(src), nodeSelector);
  }

  static <T extends Node<T, ?>> Iterable<T> getNodesOfType(Iterable<? extends Node<?, ?>> nodes, Class<T> nodeSelector) {
    return Iterators.filter(Iterators.map(nodes, n -> nodeSelector.isInstance(n)? nodeSelector.cast(n) : null), Iterators.notNullFilter());
  }
}
