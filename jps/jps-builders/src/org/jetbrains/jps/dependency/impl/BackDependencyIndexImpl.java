// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.javac.Iterators;

import java.util.*;

public abstract class BackDependencyIndexImpl implements BackDependencyIndex {
  private final String myName;
  private final MultiMaplet<ReferenceID, ReferenceID> myMap;

  protected BackDependencyIndexImpl(@NotNull String name, @NotNull MapletFactory cFactory) {
    myName = name;
    myMap = cFactory.createSetMultiMaplet();
  }

  /**
   * @param node to be indexed
   * @return direct dependencies for the given node, which should be indexed by this index
   */
  protected abstract Iterable<ReferenceID> getIndexedDependencies(@NotNull Node<?, ?> node);

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  @NotNull
  public Iterable<ReferenceID> getDependencies(@NotNull ReferenceID id) {
    Iterable<ReferenceID> nodes = myMap.get(id);
    return nodes != null? nodes : Collections.emptyList();
  }

  @Override
  public void indexNode(@NotNull Node<?, ?> node) {
    ReferenceID nodeID = node.getReferenceID();
    for (ReferenceID referentId : getIndexedDependencies(node)) {
      myMap.appendValue(referentId, nodeID);
    }
  }

  @Override
  public void integrate(Iterable<Node<?, ?>> deletedNodes, Iterable<Node<?, ?>> updatedNodes, Iterable<Pair<ReferenceID, Iterable<ReferenceID>>> indexDelta) {
    Map<ReferenceID, Set<ReferenceID>> depsToRemove = new HashMap<>();

    for (var node : deletedNodes) {
      cleanupDependencies(node, depsToRemove);
      myMap.remove(node.getReferenceID());
    }

    for (var node : updatedNodes) {
      cleanupDependencies(node, depsToRemove);
    }

    for (Pair<ReferenceID, Iterable<ReferenceID>> p : indexDelta) {
      ReferenceID nodeID = p.getFirst();
      Set<ReferenceID> deps = Iterators.collect(getDependencies(nodeID), new HashSet<>());
      Iterable<ReferenceID> toRemove = depsToRemove.get(nodeID);
      if (toRemove != null) {
        for (ReferenceID d : toRemove) {
          deps.remove(d);
        }
      }
      myMap.put(nodeID, Iterators.collect(p.getSecond(), deps));
    }
  }

  private void cleanupDependencies(Node<?, ?> node, Map<ReferenceID, Set<ReferenceID>> depsToRemove) {
    ReferenceID nodeID = node.getReferenceID();
    for (ReferenceID referentId : getIndexedDependencies(node)) {
      Set<ReferenceID> deps = depsToRemove.get(referentId);
      if (deps == null) {
        depsToRemove.put(referentId, deps = new HashSet<>());
      }
      deps.add(nodeID);
    }
  }

}
