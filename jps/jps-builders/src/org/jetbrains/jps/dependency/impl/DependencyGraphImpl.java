// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.javac.Iterators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DependencyGraphImpl extends GraphImpl implements DependencyGraph {
  private List<DifferentiateStrategy> myDifferentiateStrategies = new ArrayList<>(); // todo: fill the list

  public DependencyGraphImpl() {
    super(Containers.PERSISTENT_CONTAINER_FACTORY);
  }

  @Override
  public Delta createDelta(Iterable<NodeSource> compiledSources, Iterable<NodeSource> deletedSources) {
    return new DeltaImpl(completeSourceSet(compiledSources, deletedSources), deletedSources);
  }

  @Override
  public DifferentiateResult differentiate(Delta delta) {

    Set<Node<?, ?>> nodesBefore = Iterators.collect(Iterators.flat(Iterators.map(Iterators.flat(delta.getBaseSources(), delta.getDeletedSources()), s -> getNodes(s))), Containers.createCustomPolicySet(DiffCapable::isSame, DiffCapable::diffHashCode));
    Set<Node<?, ?>> nodesAfter = Iterators.collect(Iterators.flat(Iterators.map(delta.getSources(), s -> delta.getNodes(s))), Containers.createCustomPolicySet(DiffCapable::isSame, DiffCapable::diffHashCode));

    var diffContext = new DifferentiateContext() {
      final Set<Usage> affectedUsages = new HashSet<>();
      final Set<NodeSource> affectedSources = new HashSet<>();

      @Override
      public Graph getGraph() {
        return DependencyGraphImpl.this;
      }

      @Override
      public Delta getDelta() {
        return delta;
      }

      @Override
      public void affectUsage(Usage usage) {
        affectedUsages.add(usage);
      }

      @Override
      public void affectNodeSource(NodeSource source) {
        affectedSources.add(source);
      }
    };
    
    for (DifferentiateStrategy diffStrategy : myDifferentiateStrategies) {
      diffStrategy.differentiate(diffContext, nodesBefore, nodesAfter);
    }

    // do not process 'removed' per-source file. This works when a class comes from exactly one source, but might not work, if a class can be associated with several sources
    // better make a node-diff over all compiled sources => the sets of removed, added, deleted _nodes_ will be more accurate and reflecting reality
    List<Node<?, ?>> deletedNodes = Iterators.collect(Iterators.filter(nodesBefore, n -> !nodesAfter.contains(n)), new ArrayList<>());
    Set<NodeSource> affectedSources = new HashSet<>();
    Set<ReferenceID> dependingOnDeleted = Iterators.collect(Iterators.flat(Iterators.map(deletedNodes, n -> getDependingNodes(n.getReferenceID()))), new HashSet<>());
    for (ReferenceID dep : dependingOnDeleted) {
      for (NodeSource src : getSources(dep)) {
        affectedSources.add(src);
      }
    }
    for (ReferenceID dependent : Iterators.unique(Iterators.filter(Iterators.flat(Iterators.map(nodesAfter, n -> getDependingNodes(n.getReferenceID()))), id -> !dependingOnDeleted.contains(id)))) {
      for (NodeSource depSrc : getSources(dependent)) {
        if (!affectedSources.contains(depSrc)) {
          for (var depNode : getNodes(depSrc)) {
            if (depNode.containsAny(diffContext.affectedUsages)) {
              affectedSources.add(depSrc);
              break;
            }
          }
        }
      }
    }
    // do not include sources that were already compiled
    affectedSources.removeAll(delta.getBaseSources());
    affectedSources.removeAll(delta.getDeletedSources());
    // ensure sources explicitly marked by strategies are affected, even if these sources were compiled initially
    affectedSources.addAll(diffContext.affectedSources);

    return new DifferentiateResult() {
      @Override
      public Delta getDelta() {
        return delta;
      }

      @Override
      public Iterable<Node<?, ?>> getDeletedNodes() {
        return deletedNodes;
      }

      @Override
      public Iterable<NodeSource> getAffectedSources() {
        return affectedSources; 
      }
    };
  }

  @Override
  public void integrate(@NotNull DifferentiateResult diffResult) {
    final Delta delta = diffResult.getDelta();

    { // handle deleted nodes and sources
      Set<ReferenceID> deletedNodeIDs = new HashSet<>();
      for (var node : diffResult.getDeletedNodes()) { // the set of deleted nodes include ones corresponding to deleted sources
        myNodeToSourcesMap.remove(node.getReferenceID());
        deletedNodeIDs.add(node.getReferenceID());
      }
      for (NodeSource deletedSource : delta.getDeletedSources()) {
        for (var node : getNodes(deletedSource)) {
          // avoid the operation when known the key does not exist
          if (!deletedNodeIDs.contains(node.getReferenceID())) {
            // ensure association with deleted source is removed
            myNodeToSourcesMap.removeValue(node.getReferenceID(), deletedSource);
          }
        }
        mySourceToNodesMap.remove(deletedSource);
      }
    }

    Set<ReferenceID> deltaNodes = Iterators.collect(Iterators.map(Iterators.flat(Iterators.map(delta.getSources(), s -> delta.getNodes(s))), node -> node.getReferenceID()), new HashSet<>());
    
    var updatedNodes = Iterators.collect(Iterators.flat(Iterators.map(delta.getSources(), s -> getNodes(s))), new HashSet<>());
    for (BackDependencyIndex index : getIndices()) {
      BackDependencyIndex deltaIndex = delta.getIndex(index.getName());
      assert deltaIndex != null;
      index.integrate(diffResult.getDeletedNodes(), updatedNodes, Iterators.map(deltaNodes, id -> Pair.create(id, deltaIndex.getDependencies(id))));
    }

    for (ReferenceID nodeID : deltaNodes) {
      Set<NodeSource> sources = Iterators.collect(myNodeToSourcesMap.get(nodeID), new HashSet<>());
      sources.removeAll(delta.getBaseSources());
      Iterators.collect(delta.getSources(nodeID), sources);
      myNodeToSourcesMap.put(nodeID, sources);
    }

    for (NodeSource src : delta.getSources()) {
      mySourceToNodesMap.put(src, delta.getNodes(src));
    }
  }

  /**
   * Returns a complete set of node sources based on the input set of node sources.
   * Some nodes may be associated with multiple sources. If a source from the input set is associated with such a node,
   * the method makes sure the output set contains the rest of the sources, the node is associated with
   *
   * @param sources        set of node sources to be completed.
   * @param deletedSources registered deleted sources
   * @return complete set of node sources, containing all sources associated with nodes affected by the sources from the input set.
   */
  private Set<NodeSource> completeSourceSet(Iterable<NodeSource> sources, Iterable<NodeSource> deletedSources) {
    Set<NodeSource> result = Iterators.collect(sources, new HashSet<>()); // ensure initial sources are in teh result
    Set<NodeSource> deleted = Iterators.collect(deletedSources, new HashSet<>());

    Set<Node<?, ?>> affectedNodes = Iterators.collect(Iterators.flat(Iterators.map(Iterators.flat(result, deleted), s -> getNodes(s))), new HashSet<>());
    for (var node : affectedNodes) {
      Iterators.collect(Iterators.filter(getSources(node.getReferenceID()), s -> !result.contains(s) && !deleted.contains(s) && Iterators.filter(getNodes(s).iterator(), affectedNodes::contains).hasNext()), result);
    }
    return result;
  }

}
