// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.BackDependencyIndex;
import org.jetbrains.jps.dependency.ComparableTypeExternalizer;
import org.jetbrains.jps.dependency.Graph;
import org.jetbrains.jps.dependency.MapletFactory;
import org.jetbrains.jps.dependency.MultiMaplet;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID;
import org.jetbrains.jps.dependency.java.SubclassesIndex;
import org.jetbrains.jps.dependency.kotlin.TypealiasesIndex;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.jetbrains.jps.util.Iterators.find;

// this is a base implementation for shared functionality in both DependencyGraph and Delta
@ApiStatus.Internal
public abstract class GraphImpl implements Graph {
  private final BackDependencyIndex myDependencyIndex; // nodeId -> nodes, referencing the nodeId
  private final List<BackDependencyIndex> myIndices = new ArrayList<>();
  protected final MultiMaplet<ReferenceID, NodeSource> myNodeToSourcesMap;
  protected final MultiMaplet<NodeSource, Node<?, ?>> mySourceToNodesMap;
  private final MapletFactory myContainerFactory;
  private final IndexFactory myIndexFactory;
  private static final Set<String> ourMandatoryIndexNames = Set.of(
    NodeDependenciesIndex.NAME, SubclassesIndex.NAME, TypealiasesIndex.NAME
  );

  protected GraphImpl(@NotNull MapletFactory cFactory, IndexFactory indexFactory) {
    myContainerFactory = cFactory;
    myIndexFactory = indexFactory;
    try {

      Set<String> registered = new HashSet<>();
      for (BackDependencyIndex index : indexFactory.createIndices(cFactory)) {
        myIndices.add(index);
        registered.add(index.getName());
      }

      if (!registered.containsAll(ourMandatoryIndexNames)) {
        throw new RuntimeException("Dependency Graph must contain following mandatory indices: " + ourMandatoryIndexNames + "\n\tCurrent registered indices: " + registered);
      }
      
      myDependencyIndex = find(myIndices, idx -> NodeDependenciesIndex.NAME.equals(idx.getName()));

      // important: if multiple implementations of NodeSource are available, change to generic graph element externalizer
      ComparableTypeExternalizer<NodeSource> srcExternalizer = ComparableTypeExternalizer.forGraphElement(PathSource::new, NodeSource[]::new, Comparator.comparing(NodeSource::toString));
      myNodeToSourcesMap = cFactory.createSetMultiMaplet("node-sources-map", ComparableTypeExternalizer.forGraphElement(JvmNodeReferenceID::new, JvmNodeReferenceID[]::new, ReferenceID::compareTo), srcExternalizer);
      mySourceToNodesMap = cFactory.createSetMultiMaplet("source-nodes-map", srcExternalizer, ComparableTypeExternalizer.forAnyGraphElement(Node<?, ?>[]::new, Comparator.comparing(Node::getReferenceID)));
    }
    catch (RuntimeException e) {
      closeIgnoreErrors();
      throw e;
    }
  }

  protected final IndexFactory getIndexFactory() {
    return myIndexFactory;
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
    return myNodeToSourcesMap.get(id);
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
    return mySourceToNodesMap.get(source);
  }

  protected final void closeIgnoreErrors() {
    try {
      close();
    }
    catch (Throwable ignored) {
    }
  }

  public void close() throws IOException {
    if (myContainerFactory instanceof Closeable)  {
      ((Closeable)myContainerFactory).close();
    }
  }
  
  public void flush() throws IOException {
    if (myContainerFactory instanceof Flushable)  {
      ((Flushable)myContainerFactory).flush();
    }
  }

}
