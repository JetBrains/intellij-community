// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.java.ClassShortNameIndex;
import org.jetbrains.jps.dependency.java.JavaDifferentiateStrategy;
import org.jetbrains.jps.dependency.java.SubclassesIndex;
import org.jetbrains.jps.javac.Iterators;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class DependencyGraphImpl extends GraphImpl implements DependencyGraph {
  private static final List<DifferentiateStrategy> ourDifferentiateStrategies = List.of(
    new JavaDifferentiateStrategy()
  );

  public DependencyGraphImpl(MapletFactory containerFactory) {
    super(containerFactory);
    addIndex(new SubclassesIndex(containerFactory));
    addIndex(new ClassShortNameIndex(containerFactory));
  }

  @Override
  public Delta createDelta(Iterable<NodeSource> compiledSources, Iterable<NodeSource> deletedSources) {
    return new DeltaImpl(completeSourceSet(compiledSources, deletedSources), deletedSources);
  }

  @Override
  public DifferentiateResult differentiate(Delta delta, DifferentiateParameters params) {

    Iterable<NodeSource> deltaSources = delta.getSources();
    Set<NodeSource> allProcessedSources = Iterators.collect(Iterators.flat(Arrays.asList(delta.getBaseSources(), deltaSources, delta.getDeletedSources())), new HashSet<>());
    Set<Node<?, ?>> nodesBefore = Iterators.collect(Iterators.flat(Iterators.map(allProcessedSources, s -> getNodes(s))), Containers.createCustomPolicySet(DiffCapable::isSame, DiffCapable::diffHashCode));
    Set<Node<?, ?>> nodesAfter = Iterators.collect(Iterators.flat(Iterators.map(deltaSources, s -> delta.getNodes(s))), Containers.createCustomPolicySet(DiffCapable::isSame, DiffCapable::diffHashCode));

    // do not process 'removed' per-source file. This works when a class comes from exactly one source, but might not work, if a class can be associated with several sources
    // better make a node-diff over all compiled sources => the sets of removed, added, deleted _nodes_ will be more accurate and reflecting reality
    List<Node<?, ?>> deletedNodes = Iterators.collect(Iterators.filter(nodesBefore, n -> !nodesAfter.contains(n)), new ArrayList<>());

    if (!params.isCalculateAffected()) {
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
          return Collections.emptyList();
        }
      };
    }

    var diffContext = new DifferentiateContext() {
      private final Predicate<Node<?, ?>> ANY_CONSTRAINT = node -> true;

      final Set<NodeSource> compiledSources = deltaSources instanceof Set? (Set<NodeSource>)deltaSources : Iterators.collect(deltaSources, new HashSet<>());
      final Map<Usage, Predicate<Node<?, ?>>> affectedUsages = new HashMap<>();
      final Set<BiPredicate<Node<?, ?>, Usage>> usageQueries = new HashSet<>();
      final Set<NodeSource> affectedSources = new HashSet<>();

      @Override
      public DifferentiateParameters getParams() {
        return params;
      }

      @Override
      public @NotNull Graph getGraph() {
        return DependencyGraphImpl.this;
      }

      @Override
      public @NotNull Delta getDelta() {
        return delta;
      }

      @Override
      public boolean isCompiled(NodeSource src) {
        return compiledSources.contains(src);
      }

      @Override
      public void affectUsage(@NotNull Usage usage) {
        affectedUsages.put(usage, ANY_CONSTRAINT);
      }

      @Override
      public void affectUsage(@NotNull Usage usage, @NotNull Predicate<Node<?, ?>> constraint) {
        Predicate<Node<?, ?>> prevConstraint = affectedUsages.put(usage, constraint);
        if (prevConstraint != null) {
          affectedUsages.put(usage, prevConstraint == ANY_CONSTRAINT? ANY_CONSTRAINT : prevConstraint.or(constraint));
        }
      }

      @Override
      public void affectUsage(@NotNull BiPredicate<Node<?, ?>, Usage> usageQuery) {
        usageQueries.add(usageQuery);
      }

      @Override
      public void affectNodeSource(@NotNull NodeSource source) {
        affectedSources.add(source);
      }

      boolean isNodeAffected(Node<?, ?> node) {
        for (Usage usage : node.getUsages()) {
          Predicate<Node<?, ?>> constraint = affectedUsages.get(usage);
          if (constraint != null && constraint.test(node)) {
            return true;
          }
          for (BiPredicate<Node<?, ?>, Usage> query : usageQueries) {
            if (query.test(node, usage)) {
              return true;
            }
          }
        }
        return false;
      }
    };

    boolean incremental = true;
    for (DifferentiateStrategy diffStrategy : ourDifferentiateStrategies) {
      if (!diffStrategy.differentiate(diffContext, nodesBefore, nodesAfter)) {
        incremental = false;
        break;
      }
    }

    if (!incremental) {
      return DifferentiateResult.createNonIncremental(delta, deletedNodes);
    }

    Set<NodeSource> affectedSources = new HashSet<>();
    Set<ReferenceID> dependingOnDeleted = Iterators.collect(Iterators.flat(Iterators.map(deletedNodes, n -> getDependingNodes(n.getReferenceID()))), new HashSet<>());
    for (ReferenceID dep : dependingOnDeleted) {
      for (NodeSource src : getSources(dep)) {
        affectedSources.add(src);
      }
    }
    
    Iterable<ReferenceID> changedScopeNodes = Iterators.unique(Iterators.flat(Iterators.map(nodesAfter, n -> n.getReferenceID()), Iterators.map(diffContext.affectedUsages.keySet(), u -> u.getElementOwner())));
    for (ReferenceID dependent : Iterators.unique(Iterators.filter(Iterators.flat(Iterators.map(changedScopeNodes, id -> getDependingNodes(id))), id -> !dependingOnDeleted.contains(id)))) {
      for (NodeSource depSrc : getSources(dependent)) {
        if (!affectedSources.contains(depSrc)) {
          boolean affectSource = false;
          for (var depNode : getNodes(depSrc)) {
            if (diffContext.isNodeAffected(depNode)) {
              affectSource = true;
              for (DifferentiateStrategy strategy : ourDifferentiateStrategies) {
                if (!strategy.isIncremental(diffContext, depNode)) {
                  return DifferentiateResult.createNonIncremental(delta, deletedNodes);
                }
              }
            }
          }
          if (affectSource) {
            affectedSources.add(depSrc);
          }
        }
      }
    }
    // do not include sources that were already compiled
    affectedSources.removeAll(allProcessedSources);
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

    // handle deleted nodes and sources
    if (!Iterators.isEmpty(diffResult.getDeletedNodes())) {
      Set<NodeSource> differentiatedSources = Iterators.collect(Iterators.flat(List.of(delta.getBaseSources(), delta.getSources(), delta.getDeletedSources())), new HashSet<>());
      for (var deletedNode : diffResult.getDeletedNodes()) { // the set of deleted nodes includes ones corresponding to deleted sources
        Set<NodeSource> nodeSources = Iterators.collect(myNodeToSourcesMap.get(deletedNode.getReferenceID()), new HashSet<>());
        nodeSources.removeAll(differentiatedSources);
        if (nodeSources.isEmpty()) {
          myNodeToSourcesMap.remove(deletedNode.getReferenceID());
        }
        else {
          myNodeToSourcesMap.put(deletedNode.getReferenceID(), nodeSources);
        }
      }
    }
    for (NodeSource deletedSource : delta.getDeletedSources()) {
      mySourceToNodesMap.remove(deletedSource);
    }

    var updatedNodes = Iterators.collect(Iterators.flat(Iterators.map(delta.getSources(), s -> getNodes(s))), new HashSet<>());
    for (BackDependencyIndex index : getIndices()) {
      BackDependencyIndex deltaIndex = delta.getIndex(index.getName());
      assert deltaIndex != null;
      index.integrate(diffResult.getDeletedNodes(), updatedNodes, deltaIndex);
    }

    var deltaNodes = Iterators.unique(Iterators.map(Iterators.flat(Iterators.map(delta.getSources(), s -> delta.getNodes(s))), node -> node.getReferenceID()));
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
    // ensure initial sources are in the result
    Set<NodeSource> result = Iterators.collect(sources, new HashSet<>());          // todo: check if a special hashing-policy set is required here
    Set<NodeSource> deleted = Iterators.collect(deletedSources, new HashSet<>());

    Set<Node<?, ?>> affectedNodes = Iterators.collect(Iterators.flat(Iterators.map(Iterators.flat(result, deleted), s -> getNodes(s))), new HashSet<>());
    for (var node : affectedNodes) {
      Iterators.collect(Iterators.filter(getSources(node.getReferenceID()), s -> !result.contains(s) && !deleted.contains(s) && Iterators.filter(getNodes(s).iterator(), affectedNodes::contains).hasNext()), result);
    }
    return result;
  }

}
