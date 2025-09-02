// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.util.Iterators;

import java.util.*;

final class SourceOnlyDelta implements Delta {
  private final Set<NodeSource> myBaseSources;
  private final Set<NodeSource> myDeletedSources;
  private final Map<String, BackDependencyIndex> myIndices = new HashMap<>();

  SourceOnlyDelta(Iterable<String> indexNames, Iterable<NodeSource> baseSources, Iterable<NodeSource> deletedSources) {
    myBaseSources = Collections.unmodifiableSet(baseSources instanceof Set? (Set<? extends NodeSource>)baseSources : Iterators.collect(baseSources, new HashSet<>()));
    myDeletedSources = Collections.unmodifiableSet(deletedSources instanceof Set? (Set<? extends NodeSource>)deletedSources : Iterators.collect(deletedSources, new HashSet<>()));
    for (String name : indexNames) {
      myIndices.put(name, BackDependencyIndex.createEmpty(name));
    }
  }

  @Override
  public boolean isSourceOnly() {
    return true;
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
    // nothing here todo: throw exception?
  }

  @Override
  public Iterable<BackDependencyIndex> getIndices() {
    return myIndices.values();
  }

  @Override
  public @Nullable BackDependencyIndex getIndex(String name) {
    return myIndices.get(name);
  }

  @Override
  public @NotNull Iterable<ReferenceID> getDependingNodes(@NotNull ReferenceID id) {
    return Collections.emptyList();
  }

  @Override
  public Iterable<NodeSource> getSources(@NotNull ReferenceID id) {
    return Collections.emptyList();
  }

  @Override
  public Iterable<ReferenceID> getRegisteredNodes() {
    return Collections.emptyList();
  }

  @Override
  public Iterable<NodeSource> getSources() {
    return Collections.emptyList();
  }

  @Override
  public Iterable<Node<?, ?>> getNodes(@NotNull NodeSource source) {
    return Collections.emptyList();
  }
}
