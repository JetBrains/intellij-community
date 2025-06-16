// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.java.GeneralJvmDifferentiateStrategy;
import org.jetbrains.jps.dependency.java.SubclassesIndex;
import org.jetbrains.jps.dependency.kotlin.KotlinSourceOnlyDifferentiateStrategy;
import org.jetbrains.jps.dependency.kotlin.TypealiasesIndex;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.jetbrains.jps.util.Iterators.*;

public final class DependencyGraphImpl extends GraphImpl implements DependencyGraph {

  private static final List<DifferentiateStrategy> ourDifferentiateStrategies = List.of(new KotlinSourceOnlyDifferentiateStrategy(), new GeneralJvmDifferentiateStrategy());
  private final Set<String> myRegisteredIndices;

  public DependencyGraphImpl(MapletFactory containerFactory) {
    super(containerFactory);
    try {
      addIndex(new SubclassesIndex(containerFactory));
      addIndex(new TypealiasesIndex(containerFactory)); // todo: make registration 'pluggable', e.g. via DifferentiateStrategy
      myRegisteredIndices = Collections.unmodifiableSet(collect(map(getIndices(), index -> index.getName()), new HashSet<>()));
    }
    catch (RuntimeException e) {
      closeIgnoreErrors();
      throw e;
    }
  }

  @Override
  public Delta createDelta(Iterable<NodeSource> compiledSources, Iterable<NodeSource> deletedSources, boolean isSourceOnly) {
    Delta delta = isSourceOnly? new SourceOnlyDelta(myRegisteredIndices, compiledSources, deletedSources) : new DeltaImpl(compiledSources, deletedSources);

    Set<String> deltaIndices = collect(map(delta.getIndices(), index -> index.getName()), new HashSet<>());
    if (!myRegisteredIndices.equals(deltaIndices)) {
      throw new RuntimeException("Graph delta should contain the same set of indices as the base graph\n\tCurrent graph indices: " + myRegisteredIndices + "\n\tCurrent Delta indices: " + deltaIndices);
    }

    return delta;
  }

  @Override
  public DifferentiateResult differentiate(Delta delta, DifferentiateParameters params, Iterable<Graph> extParts) {
    boolean isIntegrable;
    Graph graphView;
    if (isEmpty(extParts)) {
      graphView = this;
      isIntegrable = true;
    }
    else {
      // the DifferentiateResult can only be safely integrated if it is built based on the data from the main graph
      graphView = CompositeGraph.create(flat(List.of(this), extParts));
      isIntegrable = false;
    }
    String sessionName = params.getSessionName();
    Iterable<NodeSource> deltaSources = delta.getSources();
    Set<NodeSource> allProcessedSources = delta.isSourceOnly()? delta.getDeletedSources() : collect(flat(List.of(delta.getBaseSources(), deltaSources, delta.getDeletedSources())), new HashSet<>());
    Set<Node<?, ?>> nodesWithErrors = params.isCompiledWithErrors()? collect(flat(map(filter(delta.getBaseSources(), s -> !contains(deltaSources, s)), graphView::getNodes)), Containers.createCustomPolicySet(DiffCapable::isSame, DiffCapable::diffHashCode)) : Set.of();

    // Important: in case of errors some sources sent to recompilation ('baseSources') might not have corresponding output classes either because a source has compilation errors
    // or because compiler stopped compilation and has not managed to compile some sources (=> produced no output for these sources).
    // In this case ignore 'baseSources' when building the set of previously available nodes, so that only successfully recompiled and deleted sources will take part in dependency analysis and affection of additional files.
    // This will also affect the contents of 'deletedNodes' set: it will be based only on those sources which were deleted or processed without errors => the current set of nodes for such files is known.
    // Nodes in the graph corresponding to those 'baseSources', for which compiler has not produced any output, are available in the 'nodeWithErrors' set and can be analysed separately.
    Set<Node<?, ?>> nodesBefore = collect(flat(map(params.isCompiledWithErrors()? flat(List.of(deltaSources, delta.getDeletedSources())) : allProcessedSources, graphView::getNodes)), Containers.createCustomPolicySet(DiffCapable::isSame, DiffCapable::diffHashCode));
    Set<Node<?, ?>> nodesAfter = delta.isSourceOnly()? Collections.emptySet() : collect(flat(map(deltaSources, delta::getNodes)), Containers.createCustomPolicySet(DiffCapable::isSame, DiffCapable::diffHashCode));

    // do not process 'removed' per-source file. This works when a class comes from exactly one source, but might not work, if a class can be associated with several sources
    // better make a node-diff over all compiled sources => the sets of removed, added, deleted _nodes_ will be more accurate and reflecting reality
    List<Node<?, ?>> deletedNodes = nodesBefore.isEmpty()? Collections.emptyList() : collect(filter(nodesBefore, n -> !nodesAfter.contains(n)), new ArrayList<>());

    if (!params.isCalculateAffected()) {
      return new DifferentiateResult() {
        @Override
        public boolean isIntegrable() {
          return isIntegrable;
        }

        @Override
        public String getSessionName() {
          return sessionName;
        }

        @Override
        public DifferentiateParameters getParameters() {
          return params;
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
      final Set<ReferenceID> deleted = collect(map(deletedNodes, Node::getReferenceID), new HashSet<>());
      final Map<Usage, Predicate<Node<?, ?>>> affectedUsages = new HashMap<>();
      final Set<Predicate<Node<?, ?>>> usageQueries = new HashSet<>();
      final Set<NodeSource> affectedSources = new HashSet<>();

      @Override
      public DifferentiateParameters getParams() {
        return params;
      }

      @Override
      public @NotNull Graph getGraph() {
        return graphView;
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
      public boolean isDeleted(ReferenceID id) {
        return deleted.contains(id);
      }

      @Override
      public void affectUsage(@NotNull Usage usage) {
        affectedUsages.put(usage, ANY_CONSTRAINT);
      }

      @Override
      public void affectUsage(@NotNull Usage usage, @NotNull Predicate<Node<?, ?>> constraint) {
        Predicate<Node<?, ?>> prevConstraint = affectedUsages.put(usage, constraint);
        if (prevConstraint != null && constraint != ANY_CONSTRAINT) {
          affectedUsages.put(usage, prevConstraint == ANY_CONSTRAINT? ANY_CONSTRAINT : prevConstraint.or(constraint));
        }
      }

      @Override
      public void affectUsage(Iterable<? extends ReferenceID> affectionScopeNodes, @NotNull Predicate<Node<?, ?>> usageQuery) {
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
        if (!affectedUsages.isEmpty()) {
          List<Predicate<Node<?, ?>>> deferred = new ArrayList<>();
          for (Predicate<Node<?, ?>> constr : filter(map(node.getUsages(), affectedUsages::get), Objects::nonNull)) {
            if (constr == ANY_CONSTRAINT) {
              return true;
            }
            deferred.add(constr);
          }
          for (Predicate<Node<?, ?>> constr : deferred) {
            if (constr.test(node)) {
              return true;
            }
          }
        }

        if (!usageQueries.isEmpty() && find(usageQueries, query -> query.test(node)) != null) {
          return true;
        }
        return false;
      }
    };

    boolean incremental = true;
    for (DifferentiateStrategy diffStrategy : ourDifferentiateStrategies) {
      if (!diffStrategy.differentiate(diffContext, nodesBefore, nodesAfter, nodesWithErrors)) {
        incremental = false;
        break;
      }
    }

    if (!incremental) {
      return DifferentiateResult.createNonIncremental("", params, delta, isIntegrable, deletedNodes);
    }

    Set<ReferenceID> dependingOnDeleted = collect(flat(map(diffContext.deleted, graphView::getDependingNodes)), new HashSet<>());
    Set<NodeSource> affectedSources = collect(flat(map(dependingOnDeleted, graphView::getSources)), new HashSet<>());

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
    Set<ReferenceID> candidates = collect(filter(flat(map(scopeNodes, graphView::getDependingNodes)), id -> !dependingOnDeleted.contains(id)), new HashSet<>());

    for (NodeSource depSrc : unique(flat(map(candidates, graphView::getSources)))) {
      if (!affectedSources.contains(depSrc) && !diffContext.affectedSources.contains(depSrc) && !allProcessedSources.contains(depSrc) && params.affectionFilter().test(depSrc)) {
        boolean affectSource = false;
        for (var depNode : filter(graphView.getNodes(depSrc), n -> candidates.contains(n.getReferenceID()))) {
          Boolean isAffected = checkAffected.apply(depNode);
          if (isAffected == null) {
            // non-incremental
            return DifferentiateResult.createNonIncremental("", params, delta, isIntegrable, deletedNodes);
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

    if (delta.isSourceOnly()) {
      // Some nodes may be associated with multiple sources. In this case ensure that all these sources are sent to compilation
      Set<NodeSource> inputSources = delta.getBaseSources();
      Set<NodeSource> deleted = delta.getDeletedSources();
      Predicate<? super NodeSource> srcFilter = DifferentiateParameters.affectableInCurrentChunk(diffContext.getParams()).and(s -> !deleted.contains(s));
      for (var node : flat(map(flat(inputSources, deleted), graphView::getNodes))) {
        Iterable<NodeSource> nodeSources = graphView.getSources(node.getReferenceID());
        if (count(nodeSources) > 1) {
          List<NodeSource> filteredNodeSources = collect(filter(nodeSources, srcFilter::test), new ArrayList<>());
          // all sources associated with the node should be either marked 'dirty' or deleted
          if (find(filteredNodeSources, s -> !inputSources.contains(s)) != null) {
            for (NodeSource s : filteredNodeSources) {
              diffContext.affectNodeSource(s);
            }
          }
        }
      }
    }

    // do not include sources that were already compiled
    affectedSources.removeAll(allProcessedSources);
    // ensure sources explicitly marked by strategies are affected, even if these sources were compiled initially
    affectedSources.addAll(diffContext.affectedSources);

    if (!delta.isSourceOnly()) {
      // complete affected file set with source-delta dependencies
      Delta affectedSourceDelta = createDelta(
        filter(affectedSources, params.belongsToCurrentCompilationChunk()::test),
        Collections.emptyList(),
        true
      );
      var srcDeltaParams = DifferentiateParametersBuilder.create(params).compiledWithErrors(false).get();
      collect(differentiate(affectedSourceDelta, srcDeltaParams).getAffectedSources(), affectedSources);
    }

    return new DifferentiateResult() {
      @Override
      public boolean isIntegrable() {
        return isIntegrable;
      }

      @Override
      public String getSessionName() {
        return sessionName;
      }

      @Override
      public DifferentiateParameters getParameters() {
        return params;
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
    if (!diffResult.isIntegrable()) {
      throw new RuntimeException("The differentiate result cannot be safely integrated");
    }
    DifferentiateParameters params = diffResult.getParameters();
    final Delta delta = diffResult.getDelta();

    // handle deleted nodes and sources
    if (!isEmpty(diffResult.getDeletedNodes())) {
      Set<NodeSource> differentiatedSources = collect(flat(List.of(params.isCompiledWithErrors()? List.of() : delta.getBaseSources(), delta.getSources(), delta.getDeletedSources())), new HashSet<>());
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

    var deltaNodes = unique(map(flat(map(delta.getSources(), delta::getNodes)), Node::getReferenceID));
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
