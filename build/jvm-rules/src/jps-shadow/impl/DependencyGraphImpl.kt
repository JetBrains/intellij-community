// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "SSBasedInspection", "ReplaceGetOrSet")

package org.jetbrains.jps.dependency.impl

import androidx.collection.MutableScatterMap
import androidx.collection.MutableScatterSet
import androidx.collection.ScatterSet
import androidx.collection.emptyScatterSet
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.collections.immutable.PersistentSet
import org.jetbrains.bazel.jvm.util.emptyList
import org.jetbrains.bazel.jvm.util.emptySet
import org.jetbrains.bazel.jvm.util.hashSet
import org.jetbrains.jps.dependency.AffectionScopeMetaUsage
import org.jetbrains.jps.dependency.Delta
import org.jetbrains.jps.dependency.DeltaEx
import org.jetbrains.jps.dependency.DependencyGraph
import org.jetbrains.jps.dependency.DifferentiateContext
import org.jetbrains.jps.dependency.DifferentiateParameters
import org.jetbrains.jps.dependency.DifferentiateResult
import org.jetbrains.jps.dependency.Graph
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.diff.Difference
import org.jetbrains.jps.dependency.java.DiffCapableHashStrategy
import org.jetbrains.jps.dependency.java.GeneralJvmDifferentiateStrategy
import org.jetbrains.jps.dependency.java.SubclassesIndex
import org.jetbrains.jps.dependency.java.notLazyDeepDiffForSets
import org.jetbrains.jps.dependency.java.notLazyDiffForSets
import org.jetbrains.jps.dependency.kotlin.KotlinSourceOnlyDifferentiateStrategy
import org.jetbrains.jps.dependency.storage.MultiMapletEx
import org.jetbrains.jps.dependency.storage.MvStoreContainerFactory
import java.util.function.Predicate

private val differentiateStrategies = arrayOf(KotlinSourceOnlyDifferentiateStrategy(), GeneralJvmDifferentiateStrategy())

class DependencyGraphImpl(containerFactory: MvStoreContainerFactory) : GraphImpl(
  containerFactory,
  SubclassesIndex(containerFactory, false),
), DependencyGraph {
  override fun close() {
  }

  @Synchronized
  override fun createDelta(
    compiledSources: Iterable<NodeSource>,
    deletedSources: Iterable<NodeSource>,
    isSourceOnly: Boolean,
  ): Delta {
    if (isSourceOnly) {
      return SourceOnlyDelta(compiledSources as Set<NodeSource>, deletedSources as Set<NodeSource>)
    }

    // updateMappingsOnRoundCompletion uses Iterators.map without an explicit size, cannot fix it for now

    val delta = DeltaImpl(toSet(compiledSources), toSet(deletedSources))
    if (delta.indices.map { it.name } != indices.map { it.name }) {
      throw RuntimeException(
        "Graph delta should contain the same set of indices as the base graph\n\t" +
          "Current graph indices: $registeredIndices\n\tCurrent Delta indices: ${delta.indices}"
      )
    }
    return delta
  }

  @Synchronized
  override fun differentiate(delta: Delta, params: DifferentiateParameters): DifferentiateResult {
    val allProcessedSources: Set<NodeSource> = if (delta.isSourceOnly) {
      delta.deletedSources
    }
    else {
      val all = hashSet<NodeSource>(delta.baseSources.size + delta.deletedSources.size)
      all.addAll(delta.baseSources)
      all.addAll(delta.sources)
      all.addAll(delta.deletedSources)
      all
    }

    val nodesAfter = if (delta.isSourceOnly) {
      emptySet()
    }
    else {
      val result = ObjectOpenCustomHashSet<Node<*, *>>(DiffCapableHashStrategy)
      (delta as DeltaEx).getSourceToNodesMap().forEachValue { nodes ->
        result.ensureCapacity(nodes.size)
        nodes.forEach { result.add(it) }
      }
      result
    }
    return differentiate(
      delta = delta,
      allProcessedSources = allProcessedSources,
      nodesAfter = nodesAfter,
      params = params,
      getBeforeNodes = { getNodes(it) },
    )
  }

  @Synchronized
  fun differentiate(
    delta: Delta,
    allProcessedSources: Set<NodeSource>,
    // a set with DiffCapableHashStrategy is expected
    nodesAfter: Set<Node<*, *>>,
    params: DifferentiateParameters,
    getBeforeNodes: (source: NodeSource) -> Iterable<Node<*, *>>,
  ): DifferentiateResult {
    val sessionName = params.sessionName
    val deltaSources = delta.sources
    val nodesWithErrors: Set<Node<*, *>> = if (params.isCompiledWithErrors) {
      val result = ObjectOpenCustomHashSet<Node<*, *>>(DiffCapableHashStrategy)
      for (source in delta.baseSources) {
        if (deltaSources.contains(source)) {
          result.addAll(getBeforeNodes(source))
        }
      }
      result
    }
    else {
      emptySet()
    }

    // Important: in case of errors, some sources sent to recompilation (`baseSources`)
    // might not have corresponding output classes either because a source has compilation errors
    // or because the compiler stopped compilation and has not managed to compile some sources
    // (=> produced no output for these sources).
    // In this case, ignore 'baseSources' when building the set of previously available nodes
    // so that only successfully recompiled and deleted sources will take part in dependency analysis and affection of additional files.
    // This will also affect the contents of the `deletedNodes` set: it will be based only on those sources
    // which were deleted or processed without errors => the current set of nodes for such files is known.
    // Nodes in the graph corresponding to those 'baseSources',
    // for which the compiler has not produced any output, are available in the 'nodeWithErrors' set and can be analyzed separately.
    val nodesBefore = ObjectOpenCustomHashSet<Node<*, *>>(DiffCapableHashStrategy)
    if (params.isCompiledWithErrors) {
      deltaSources.flatMapTo(nodesBefore) { getBeforeNodes(it) }
      delta.deletedSources.flatMapTo(nodesBefore) { getBeforeNodes(it) }
    }
    else {
      allProcessedSources.flatMapTo(nodesBefore) { getBeforeNodes(it) }
    }

    // Do not process 'removed' per-source file.
    // This works when a class comes from exactly one source.
    // However, it might not work if a class can be associated with several sources
    // better make a node-diff over all compiled sources => the sets of removed,
    // added, deleted _nodes_ will be more accurate and reflect reality
    val deletedNodes = if (nodesBefore.isEmpty()) emptyList() else nodesBefore.filter { !nodesAfter.contains(it) }

    if (!params.isCalculateAffected) {
      return object : DifferentiateResult {
        override fun getSessionName() = sessionName

        override fun getParameters() = params

        override fun getDelta() = delta

        override fun getDeletedNodes() = deletedNodes

        override fun getAffectedSources() = java.util.Set.of<NodeSource>()
      }
    }

    val diffContext = object : DifferentiateContext {
      private val ANY_CONSTRAINT = Predicate<Node<*, *>> { true }

      @JvmField val compiledSources = if (deltaSources is Set<*>) deltaSources else ObjectOpenHashSet(deltaSources.iterator())
      @JvmField val deleted = deletedNodes.mapTo(hashSet(deletedNodes.size)) { it.referenceID }
      @JvmField val affectedUsages = MutableScatterMap<Usage, Predicate<Node<*, *>>>()
      @JvmField val usageQueries = MutableScatterSet<Predicate<Node<*, *>>>()
      @JvmField val affectedSources = MutableScatterSet<NodeSource>()

      override fun getParams(): DifferentiateParameters = params

      override fun getGraph(): Graph = this@DependencyGraphImpl

      override fun getDelta(): Delta = delta

      override fun isCompiled(src: NodeSource): Boolean {
        return compiledSources.contains(src)
      }

      override fun isDeleted(id: ReferenceID): Boolean {
        return deleted.contains(id)
      }

      override fun affectUsage(usage: Usage) {
        affectedUsages.put(usage, ANY_CONSTRAINT)
      }

      override fun affectUsage(usage: Usage, constraint: Predicate<Node<*, *>>) {
        val prevConstraint = affectedUsages.put(usage, constraint)
        if (prevConstraint != null && constraint != ANY_CONSTRAINT) {
          affectedUsages.put(usage, if (prevConstraint == ANY_CONSTRAINT) ANY_CONSTRAINT else prevConstraint.or(constraint))
        }
      }

      override fun affectUsage(affectionScopeNodes: Iterable<ReferenceID>, usageQuery: Predicate<Node<*, *>>) {
        for (id in affectionScopeNodes) {
          affectUsage(AffectionScopeMetaUsage(id))
        }
        usageQueries.add(usageQuery)
      }

      override fun affectNodeSource(source: NodeSource) {
        affectedSources.add(source)
      }

      fun isNodeAffected(node: Node<*, *>): Boolean {
        if (!affectedUsages.isEmpty()) {
          val deferred = ArrayList<Predicate<Node<*, *>>>()
          for (usage in node.getUsages()) {
            val constr = affectedUsages.get(usage) ?: continue
            if (constr === ANY_CONSTRAINT) {
              return true
            }
            deferred.add(constr)
          }
          for (constr in deferred) {
            if (constr.test(node)) {
              return true
            }
          }
        }

        if (!usageQueries.isEmpty() && usageQueries.any { it.test(node) }) {
          return true
        }
        return false
      }
    }

    for (diffStrategy in differentiateStrategies) {
      if (!diffStrategy.differentiate(diffContext, nodesBefore, nodesAfter, nodesWithErrors)) {
       return DifferentiateResult.createNonIncremental("", params, delta, deletedNodes)
      }
    }

    val dependingOnDeleted = MutableScatterSet<ReferenceID>()
    for (refId in diffContext.deleted) {
      dependingOnDeleted.addAll(getDependingNodes(refId))
    }
    val affectedSources = MutableScatterSet<NodeSource>()
    dependingOnDeleted.forEach {
      affectedSources.addAll(getSources(it))
    }

    val affectedNodeCache = Object2ObjectOpenCustomHashMap<Node<*, *>, Boolean>(DiffCapableHashStrategy)
    val checkAffected: (Node<*, *>) -> Boolean? = { k ->
      affectedNodeCache.computeIfAbsent(k) { n: Node<*, *> ->
        if (!diffContext.isNodeAffected(n)) {
          return@computeIfAbsent false
        }
        for (strategy in differentiateStrategies) {
          if (!strategy.isIncremental(diffContext, n)) {
            return@computeIfAbsent null
          }
        }
        true
      }
    }

    val candidates = MutableScatterSet<ReferenceID>()
    run {
      val visited = MutableScatterSet<ReferenceID>()
      diffContext.affectedUsages.forEachKey { usage ->
        val owner = usage.elementOwner
        if (visited.add(owner)) {
          for (id in getDependingNodes(owner)) {
            if (!dependingOnDeleted.contains(id)) {
              candidates.add(id)
            }
          }
        }
      }
    }

    val visited = MutableScatterSet<NodeSource>()
    candidates.forEach { candidate ->
      for (depSrc in getSources(candidate)) {
        if (!visited.add(depSrc)) {
          continue
        }

        if (!affectedSources.contains(depSrc) &&
          !diffContext.affectedSources.contains(depSrc) &&
          !allProcessedSources.contains(depSrc) &&
          params.affectionFilter().test(depSrc)) {
          var affectSource = false
          for (depNode in getBeforeNodes(depSrc)) {
            if (!candidates.contains(depNode.referenceID)) {
              continue
            }

            val isAffected = checkAffected(depNode)
            if (isAffected == null) {
              // non-incremental
              return DifferentiateResult.createNonIncremental("", params, delta, deletedNodes)
            }
            if (isAffected) {
              affectSource = true
            }
          }
          if (affectSource) {
            affectedSources.add(depSrc)
          }
        }
      }
    }

    if (delta.isSourceOnly) {
      // Some nodes may be associated with multiple sources. In this case, ensure that all these sources are sent to compilation
      val inputSources = delta.baseSources
      val deleted = delta.deletedSources
      val srcFilter = DifferentiateParameters.affectableInCurrentChunk(diffContext.getParams()).and { !deleted.contains(it) }
      for (node in (inputSources.asSequence() + deleted).flatMap { getBeforeNodes(it) }) {
        val nodeSources = getSources(node.referenceID)
        if (nodeSources.count() > 1) {
          val filteredNodeSources = nodeSources.filter { srcFilter.test(it) }
          // all sources associated with the node should be either marked 'dirty' or deleted
          if (filteredNodeSources.any { !inputSources.contains(it) }) {
            for (s in filteredNodeSources) {
              diffContext.affectNodeSource(s)
            }
          }
        }
      }
    }

    // do not include sources that were already compiled
    affectedSources.removeAll(allProcessedSources)
    // ensure sources explicitly marked by strategies are affected, even if these sources were compiled initially
    diffContext.affectedSources.forEach { affectedSources.add(it) }

    if (!delta.isSourceOnly) {
      // complete the affected file set with source-delta dependencies
      val compiledSources = ObjectOpenHashSet<NodeSource>(affectedSources.size)
      affectedSources.forEach {
        if (params.belongsToCurrentCompilationChunk().test(it)) {
          compiledSources.add(it)
        }
      }
      val affectedSourceDelta = createDelta(compiledSources = compiledSources, deletedSources = emptySet(), isSourceOnly = true)
      val srcDeltaParams = DifferentiateParametersBuilder.create(params).compiledWithErrors(false).get()
      affectedSources.addAll(differentiate(affectedSourceDelta, srcDeltaParams).affectedSources)
    }

    return object : DifferentiateResult {
      override fun getSessionName() = sessionName

      override fun getParameters() = params

      override fun getDelta() = delta

      override fun getDeletedNodes() = deletedNodes

      override fun getAffectedSources() = affectedSources.asSet()
    }
  }

  @Synchronized
  override fun integrate(diffResult: DifferentiateResult) {
    val params = diffResult.parameters
    val delta = diffResult.delta

    // handle deleted nodes and sources
    if (diffResult.deletedNodes.any()) {
      val differentiatedSources = MutableScatterSet<NodeSource>()
      if (!params.isCompiledWithErrors) {
        differentiatedSources.addAll(delta.baseSources)
      }
      differentiatedSources.addAll(delta.sources)
      differentiatedSources.addAll(delta.deletedSources)

      // the set of deleted nodes includes ones corresponding to deleted sources
      for (deletedNode in diffResult.deletedNodes) {
        nodeToSourcesMap.removeValues(deletedNode.referenceID, differentiatedSources)
      }
    }

    for (deletedSource in delta.deletedSources) {
      sourceToNodesMap.remove((deletedSource as PathSource).pathHash)
    }

    val updatedNodes = delta.sources.flatMapTo(ObjectOpenCustomHashSet(DiffCapableHashStrategy)) { getNodes(it) }
    for (index in indices) {
      index.integrate(diffResult.deletedNodes, updatedNodes, delta.getIndex(index.name))
    }

    updateSourceToNodeSetMap(delta, nodeToSourcesMap)

    if (delta is DeltaEx) {
      delta.getSourceToNodesMap().forEach { source, nodes ->
        updateSourceToNodeSetMapByDiff(
          map = sourceToNodesMap,
          key = (source as PathSource).pathHash,
          dataAfter = nodes,
        ) { past, now -> deepDiffForNodes(past, now) }
      }
    }
    else {
      for (deltaSource in delta.sources) {
        require(delta.getNodes(deltaSource).none()) {
          "Expected that SourceDelta.getNodes() always returns an empty list"
        }

        updateSourceToNodeSetMapByDiff(
          map = sourceToNodesMap,
          key = (deltaSource as PathSource).pathHash,
          dataAfter = emptyScatterSet()
        ) { past, now -> deepDiffForNodes(past, now) }
      }
    }
  }
}

private fun toSet(compiledSources: Iterable<NodeSource>): Set<NodeSource> {
  if (compiledSources is Set<NodeSource>) {
    return compiledSources
  }
  else {
    return ObjectOpenHashSet(compiledSources.iterator())
  }
}

private fun updateSourceToNodeSetMap(delta: Delta, nodeToSourcesMap: MultiMapletEx<ReferenceID, NodeSource>) {
  val deltaNodes = delta.sources.asSequence().flatMap { delta.getNodes(it) }.map { it.referenceID }
  val visited = MutableScatterSet<ReferenceID>()
  val sourcesAfter = MutableScatterSet<NodeSource>()
  for (nodeId in deltaNodes) {
    if (!visited.add(nodeId)) {
      continue
    }

    sourcesAfter.clear()

    @Suppress("UNCHECKED_CAST")
    sourcesAfter.addAll(nodeToSourcesMap.get(nodeId) as Collection<NodeSource>)
    sourcesAfter.removeAll(delta.baseSources)
    @Suppress("UNCHECKED_CAST")
    sourcesAfter.addAll(delta.getSources(nodeId) as Collection<NodeSource>)

    updateSourceToNodeSetMapByDiff(map = nodeToSourcesMap, key = nodeId, dataAfter = sourcesAfter) { past, now -> notLazyDiffForSets(past, now) }
  }
}

private fun deepDiffForNodes(
  past: Collection<Node<*, *>>,
  now: ScatterSet<Node<*, *>>
): Difference.Specifier<Node<*, *>, Difference>? {
  @Suppress("UNCHECKED_CAST")
  return notLazyDeepDiffForSets(
    past as Collection<Node<*, Difference>>,
    now as ScatterSet<Node<*, Difference>>,
  ) as Difference.Specifier<Node<*, *>, Difference>?
}

private inline fun <K : Any, V : Any> updateSourceToNodeSetMapByDiff(
  map: MultiMapletEx<K, V>,
  key: K,
  dataAfter: ScatterSet<V>,
  diffComparator: (PersistentSet<V>, ScatterSet<V>) -> Difference.Specifier<V, *>?,
) {
  @Suppress("UNCHECKED_CAST")
  val dataBefore = map.get(key) as PersistentSet<V>
  val beforeEmpty = dataBefore.isEmpty()
  val afterEmpty = dataAfter.isEmpty()
  if (beforeEmpty || afterEmpty) {
    if (!afterEmpty) {
      // so, before is empty - put instead of appendValues (put is more performant)
      map.put(key, dataAfter)
    }
    else if (!beforeEmpty) {
      map.remove(key)
    }
  }
  else {
    val diff = diffComparator(dataBefore, dataAfter) ?: return
    if (!diff.unchanged()) {
      if (diff.removed().none() && diff.changed().none()) {
        map.appendValues(key, diff.added())
      }
      else {
        map.put(key, dataAfter)
      }
    }
  }
}