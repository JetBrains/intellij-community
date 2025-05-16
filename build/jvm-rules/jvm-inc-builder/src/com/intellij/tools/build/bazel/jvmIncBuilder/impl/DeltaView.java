// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;

import java.util.*;

import static org.jetbrains.jps.util.Iterators.*;

public class DeltaView implements Delta {
  private final Set<NodeSource> mySources;
  private final Set<NodeSource> myDeletedSources;
  private final Graph myDelegate;
  private final HashSet<ReferenceID> myNodes;
  private final Map<String, ValuesFilteredBackDependencyIndex> myIndexMap = new HashMap<>();

  public DeltaView(Set<NodeSource> sources, Set<NodeSource> deletedSources, Graph delegate) {
    mySources = Collections.unmodifiableSet(sources);
    myDeletedSources = Collections.unmodifiableSet(deletedSources);
    myDelegate = delegate;
    myNodes = collect(map(flat(map(sources, delegate::getNodes)), Node::getReferenceID), new HashSet<>());
  }

  @Override
  public Set<NodeSource> getDeletedSources() {
    return myDeletedSources;
  }

  @Override
  public Set<NodeSource> getBaseSources() {
    return Set.of();
  }

  @Override
  public Iterable<NodeSource> getSources(@NotNull ReferenceID id) {
    return myNodes.contains(id)? myDelegate.getSources(id) : Set.of();
  }

  @Override
  public Iterable<NodeSource> getSources() {
    return mySources;
  }


  @Override
  public void associate(@NotNull Node<?, ?> node, @NotNull Iterable<NodeSource> sources) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSourceOnly() {
    return false;
  }

  @Override
  public Iterable<BackDependencyIndex> getIndices() {
    return map(myDelegate.getIndices(), idx -> myIndexMap.computeIfAbsent(idx.getName(), n -> ValuesFilteredBackDependencyIndex.create(idx, myNodes::contains)));
  }

  @Override
  public @Nullable BackDependencyIndex getIndex(String name) {
    return myIndexMap.computeIfAbsent(name, n -> {
      BackDependencyIndex index = myDelegate.getIndex(n);
      return ValuesFilteredBackDependencyIndex.create(index != null? index : BackDependencyIndex.createEmpty(n), myNodes::contains);
    }) ;
  }

  @Override
  public @NotNull Iterable<ReferenceID> getDependingNodes(@NotNull ReferenceID id) {
    return filter(myDelegate.getDependingNodes(id), myNodes::contains);
  }

  @Override
  public Iterable<ReferenceID> getRegisteredNodes() {
    return myNodes;
  }

  @Override
  public Iterable<Node<?, ?>> getNodes(@NotNull NodeSource source) {
    return mySources.contains(source)? myDelegate.getNodes(source) : Set.of();
  }

}
