// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.java.SubclassesIndex;
import org.jetbrains.jps.javac.Iterators;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/*
   1. The delta contains nodes, sources, and their mappings that are known to be compiled in corresponding compilation round
   2. If a node was associated with several sources, all those sources are expected to be processed by the compiler in this compilation round
   3. if a source is contained in the delta, this means that a source was either
      - modified or
      - deleted (non-existing at the moment of compilation) or
      - unchanged, but corresponding to a node, that was produced in this compilation round (the node has been recompiled, because another source corresponding to the node was modified )
   4. It is expected that the source->nodes mapping is complete:
      all nodes, corresponding to some source from the delta, are registered in the delta and every source mentioned in the delta contains complete list of nodes that correspond to it)
 */

public class DeltaImpl extends GraphImpl implements Delta {
  private final Set<NodeSource> myBaseSources;
  private final Set<NodeSource> myDeletedSources;
  
  public DeltaImpl(Set<NodeSource> baseSources, Iterable<NodeSource> deletedSources) {
    super(Containers.MEMORY_CONTAINER_FACTORY);
    addIndex(new SubclassesIndex(Containers.MEMORY_CONTAINER_FACTORY));
    myBaseSources = Collections.unmodifiableSet(baseSources);
    myDeletedSources = Collections.unmodifiableSet(Iterators.collect(deletedSources, new HashSet<>()));
  }

  @Override
  public Set<NodeSource> getBaseSources() {
    return myBaseSources;
  }

  @Override
  public Set<NodeSource> getDeletedSources() {
    return myDeletedSources;
  }

  @Override
  public void associate(@NotNull Node<?, ?> node, @NotNull Iterable<NodeSource> sources) {
    ReferenceID nodeID = node.getReferenceID();
    for (NodeSource src : sources) {
      myNodeToSourcesMap.appendValue(nodeID, src);
      mySourceToNodesMap.appendValue(src, node);
    }
    // deduce dependencies
    for (BackDependencyIndex index : getIndices()) {
      index.indexNode(node);
    }
  }

}
