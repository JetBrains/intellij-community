// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.java.ClassShortNameIndex;
import org.jetbrains.jps.dependency.java.GeneralJvmDifferentiateStrategy;
import org.jetbrains.jps.dependency.java.SubclassesIndex;

import java.io.IOException;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.jetbrains.jps.javac.Iterators.*;

public final class DependencyGraphImpl extends GraphImpl implements DependencyGraph {

  private static final List<DifferentiateStrategy> ourDifferentiateStrategies = List.of(new GeneralJvmDifferentiateStrategy());
  private final Set<String> myRegisteredIndices;

  public DependencyGraphImpl(MapletFactory containerFactory) throws IOException {
    super(containerFactory);
    addIndex(new SubclassesIndex(containerFactory));
    addIndex(new ClassShortNameIndex(containerFactory));
    myRegisteredIndices = Collections.unmodifiableSet(collect(map(getIndices(), index -> index.getName()), new HashSet<>()));
  }

  @Override
  public Delta createDelta(Iterable<NodeSource> compiledSources, Iterable<NodeSource> deletedSources) throws IOException {
    DeltaImpl delta = new DeltaImpl(completeSourceSet(compiledSources, deletedSources), deletedSources);

    Set<String> deltaIndices = collect(map(delta.getIndices(), index -> index.getName()), new HashSet<>());
    if (!myRegisteredIndices.equals(deltaIndices)) {
      throw new RuntimeException("Graph delta should contain the same set of indices as the base graph\n\tCurrent graph indices: " + myRegisteredIndices + "\n\tCurrent Delta indices: " + deltaIndices);
    }

    return delta;
  }

  @Override
  public DifferentiateResult differentiate(Delta delta, DifferentiateParameters params) {

    String sessionName = params.getSessionName();
    Iterable<NodeSource> deltaSources = delta.getSources();
    Set<NodeSource> allProcessedSources = collect(flat(Arrays.asList(delta.getBaseSources(), deltaSources, delta.getDeletedSources())), new HashSet<>());
    Set<Node<?, ?>> nodesBefore = collect(flat(map(allProcessedSources, this::getNodes)), Containers.createCustomPolicySet(DiffCapable::isSame, DiffCapable::diffHashCode));
    Set<Node<?, ?>> nodesAfter = collect(flat(map(deltaSources, delta::getNodes)), Containers.createCustomPolicySet(DiffCapable::isSame, DiffCapable::diffHashCode));

    // do not process 'removed' per-source file. This works when a class comes from exactly one source, but might not work, if a class can be associated with several sources
    // better make a node-diff over all compiled sources => the sets of removed, added, deleted _nodes_ will be more accurate and reflecting reality
    List<Node<?, ?>> deletedNodes = collect(filter(nodesBefore, n -> !nodesAfter.contains(n)), new ArrayList<>());

    if (!params.isCalculateAffected()) {
      return new DifferentiateResult() {
        @Override
        public String getSessionName() {
          return sessionName;
        }

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

      final Set<NodeSource> compiledSources = deltaSources instanceof Set? (Set<NodeSource>)deltaSources : collect(deltaSources, new HashSet<>());
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
      public void affectUsage(Iterable<? extends ReferenceID> affectionScopeNodes, @NotNull BiPredicate<Node<?, ?>, Usage> usageQuery) {
        for (Usage u : map(affectionScopeNodes, AffectionScopeMetaUsage::new)) {
          affectUsage(u);
        }
        usageQueries.add(usageQuery);
      }

      @Override
      public void affectNodeSource(@NotNull NodeSource source) {
        affectedSources.add(source);
      }

      boolean isNodeAffected(Node<?, ?> node) {
        if (!affectedUsages.isEmpty() && find(filter(map(node.getUsages(), affectedUsages::get), Objects::nonNull), constr -> constr.test(node)) != null) {
          return true;
        }
        if (!usageQueries.isEmpty() && find(node.getUsages(), u -> find(usageQueries, query -> query.test(node, u)) != null) != null) {
          return true;
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
      return DifferentiateResult.createNonIncremental("", delta, deletedNodes);
    }

    Set<ReferenceID> dependingOnDeleted = collect(flat(map(deletedNodes, n -> getDependingNodes(n.getReferenceID()))), new HashSet<>());
    Set<NodeSource> affectedSources = collect(flat(map(dependingOnDeleted, this::getSources)), new HashSet<>());


    Map<Node<?, ?>, Boolean> affectedNodeCache = Containers.createCustomPolicyMap(DiffCapable::isSame, DiffCapable::diffHashCode);
    Function<Node<?, ?>, Boolean> checkAffected = k -> affectedNodeCache.computeIfAbsent(k, n -> {
      if (!diffContext.isNodeAffected(n)) {
        return Boolean.FALSE;
      }
      for (DifferentiateStrategy strategy : ourDifferentiateStrategies) {
        if (!strategy.isIncremental(diffContext, n)) {
          return null;
        }
      }
      return Boolean.TRUE;
    });

    Iterable<ReferenceID> scopeNodes = unique(map(diffContext.affectedUsages.keySet(), Usage::getElementOwner));
    Set<ReferenceID> candidates = collect(filter(flat(map(scopeNodes, this::getDependingNodes)), id -> !dependingOnDeleted.contains(id)), new HashSet<>());

    for (NodeSource depSrc : unique(flat(map(candidates, this::getSources)))) {
      if (!affectedSources.contains(depSrc) && !diffContext.affectedSources.contains(depSrc) && !allProcessedSources.contains(depSrc) && params.affectionFilter().test(depSrc)) {
        boolean affectSource = false;
        for (var depNode : filter(getNodes(depSrc), n -> candidates.contains(n.getReferenceID()))) {
          Boolean isAffected = checkAffected.apply(depNode);
          if (isAffected == null) {
            // non-incremental
            return DifferentiateResult.createNonIncremental("", delta, deletedNodes);
          }
          if (isAffected) {
            affectSource = true;
          }
        }
        if (affectSource) {
          affectedSources.add(depSrc);
        }
      }
    }
    // do not include sources that were already compiled
    affectedSources.removeAll(allProcessedSources);
    // ensure sources explicitly marked by strategies are affected, even if these sources were compiled initially
    affectedSources.addAll(diffContext.affectedSources);

    return new DifferentiateResult() {
      @Override
      public String getSessionName() {
        return sessionName;
      }

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
    if (!isEmpty(diffResult.getDeletedNodes())) {
      Set<NodeSource> differentiatedSources = collect(flat(List.of(delta.getBaseSources(), delta.getSources(), delta.getDeletedSources())), new HashSet<>());
      for (var deletedNode : diffResult.getDeletedNodes()) { // the set of deleted nodes includes ones corresponding to deleted sources
        Set<NodeSource> nodeSources = collect(myNodeToSourcesMap.get(deletedNode.getReferenceID()), new HashSet<>());
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

    var updatedNodes = collect(flat(map(delta.getSources(), this::getNodes)), Containers.createCustomPolicySet(DiffCapable::isSame, DiffCapable::diffHashCode));
    for (BackDependencyIndex index : getIndices()) {
      BackDependencyIndex deltaIndex = delta.getIndex(index.getName());
      assert deltaIndex != null;
      index.integrate(diffResult.getDeletedNodes(), updatedNodes, deltaIndex);
    }

    var deltaNodes = unique(map(flat(map(delta.getSources(), delta::getNodes)), node -> node.getReferenceID()));
    for (ReferenceID nodeID : deltaNodes) {
      Set<NodeSource> sourcesAfter = collect(myNodeToSourcesMap.get(nodeID), new HashSet<>());
      sourcesAfter.removeAll(delta.getBaseSources());
      collect(delta.getSources(nodeID), sourcesAfter);

      myNodeToSourcesMap.update(nodeID, sourcesAfter, Difference::diff);
    }

    for (NodeSource src : delta.getSources()) {
      //noinspection unchecked
      mySourceToNodesMap.update(src, delta.getNodes(src), (past, now) -> new Difference.Specifier<>() {
        private final Difference.Specifier<Node, ?> diff = Difference.deepDiff(Graph.getNodesOfType(past, Node.class), Graph.getNodesOfType(now, Node.class));

        @Override
        public Iterable<Node<?, ?>> added() {
          return map(diff.added(), n -> (Node<?, ?>)n);
        }

        @Override
        public Iterable<Node<?, ?>> removed() {
          return map(diff.removed(), n -> (Node<?, ?>)n);
        }

        @Override
        public Iterable<Difference.Change<Node<?, ?>, Difference>> changed() {
          return map(diff.changed(), ch -> new DiffChangeAdapter(ch));
        }

        @Override
        public boolean unchanged() {
          return diff.unchanged();
        }
      });
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
    Set<NodeSource> result = collect(sources, new HashSet<>());          // todo: check if a special hashing-policy set is required here
    Set<NodeSource> deleted = collect(deletedSources, new HashSet<>());

    Set<Node<?, ?>> affectedNodes = collect(flat(map(flat(result, deleted), s -> getNodes(s))), new HashSet<>());
    for (var node : affectedNodes) {
      collect(filter(getSources(node.getReferenceID()), s -> !result.contains(s) && !deleted.contains(s) && filter(getNodes(s).iterator(), affectedNodes::contains).hasNext()), result);
    }
    return result;
  }

  private static final class DiffChangeAdapter implements Difference.Change<Node<?, ?>, Difference> {

    private final Difference.Change<Node, ?> myDelegate;

    DiffChangeAdapter(Difference.Change<Node, ?> delegate) {
      myDelegate = delegate;
    }

    @Override
    public Node<?, ?> getPast() {
      return myDelegate.getPast();
    }

    @Override
    public Node<?, ?> getNow() {
      return myDelegate.getNow();
    }

    @Override
    public Difference getDiff() {
      return myDelegate.getDiff();
    }
  }
}
