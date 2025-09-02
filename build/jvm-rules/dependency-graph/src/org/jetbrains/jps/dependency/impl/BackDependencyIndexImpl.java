// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID;
import org.jetbrains.jps.util.Iterators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class BackDependencyIndexImpl implements BackDependencyIndex {
  private final String myName;
  private final MultiMaplet<ReferenceID, ReferenceID> myMap;

  protected BackDependencyIndexImpl(@NotNull String name, @NotNull MapletFactory cFactory) {
    myName = name;
    // important: if multiple implementations of ReferenceID are available, change to createMultitypeExternalizer
    Externalizer<ReferenceID> ext = Externalizer.forGraphElement(JvmNodeReferenceID::new, JvmNodeReferenceID[]::new);
    myMap = cFactory.createSetMultiMaplet(name, ext, ext);
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
  public Iterable<ReferenceID> getKeys() {
    return myMap.getKeys();
  }

  @Override
  public @NotNull Iterable<ReferenceID> getDependencies(@NotNull ReferenceID id) {
    return myMap.get(id);
  }

  @Override
  public void indexNode(@NotNull Node<?, ?> node) {
    ReferenceID nodeID = node.getReferenceID();
    for (ReferenceID referentId : getIndexedDependencies(node)) {
      myMap.appendValue(referentId, nodeID);
    }
  }

  @Override
  public void integrate(Iterable<Node<?, ?>> deletedNodes, Iterable<Node<?, ?>> updatedNodes, BackDependencyIndex deltaIndex) {
    Map<ReferenceID, Set<ReferenceID>> depsToRemove = new HashMap<>();

    for (var node : deletedNodes) {
      cleanupDependencies(node, depsToRemove);
      // corner case, relevant to situations when keys in this index are actually real node IDs
      // if a node gets deleted, corresponding index key gets deleted to: this allows to ensure there is no outdated information in the index
      // If later a new node with the same ID is added, the previous index data for this ID will not interfere with the new state.
      myMap.remove(node.getReferenceID());
    }

    for (var node : updatedNodes) {
      cleanupDependencies(node, depsToRemove);
    }

    for (ReferenceID id : Iterators.unique(Iterators.flat(deltaIndex.getKeys(), depsToRemove.keySet()))) {
      Set<ReferenceID> toRemove = depsToRemove.get(id);
      Iterable<ReferenceID> toAdd = deltaIndex.getDependencies(id);
      if (!Iterators.isEmpty(toRemove)) {
        if (toAdd instanceof Set) {
          toRemove.removeAll((Set<?>)toAdd);
        }
        else {
          for (ReferenceID refId : toAdd) {
            toRemove.remove(refId);
          }
        }
        myMap.removeValues(id, toRemove);
      }
      myMap.appendValues(id, toAdd);
    }
  }

  private void cleanupDependencies(Node<?, ?> node, Map<ReferenceID, Set<ReferenceID>> depsToRemove) {
    ReferenceID nodeID = node.getReferenceID();
    for (ReferenceID referentId : getIndexedDependencies(node)) {
      depsToRemove.computeIfAbsent(referentId, k -> new HashSet<>()).add(nodeID);
    }
  }

}
