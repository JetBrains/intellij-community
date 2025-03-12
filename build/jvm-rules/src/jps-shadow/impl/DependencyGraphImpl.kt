@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "SSBasedInspection", "ReplaceGetOrSet")

package org.jetbrains.jps.dependency.impl

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import org.jetbrains.bazel.jvm.emptyList
import org.jetbrains.bazel.jvm.emptySet
import org.jetbrains.bazel.jvm.hashSet
import org.jetbrains.jps.dependency.AffectionScopeMetaUsage
import org.jetbrains.jps.dependency.Delta
import org.jetbrains.jps.dependency.DependencyGraph
import org.jetbrains.jps.dependency.DifferentiateContext
import org.jetbrains.jps.dependency.DifferentiateParameters
import org.jetbrains.jps.dependency.DifferentiateResult
import org.jetbrains.jps.dependency.Graph
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.diff.Difference
import org.jetbrains.jps.dependency.java.DiffCapableHashStrategy
import org.jetbrains.jps.dependency.java.GeneralJvmDifferentiateStrategy
import org.jetbrains.jps.dependency.java.SubclassesIndex
import org.jetbrains.jps.dependency.java.deepDiffForSets
import org.jetbrains.jps.dependency.java.diffForSets
import org.jetbrains.jps.dependency.kotlin.KotlinSourceOnlyDifferentiateStrategy
import org.jetbrains.jps.dependency.storage.MvStoreContainerFactory
import java.util.function.Predicate

private val differentiateStrategies = arrayOf(KotlinSourceOnlyDifferentiateStrategy(), GeneralJvmDifferentiateStrategy())

class DependencyGraphImpl(containerFactory: MvStoreContainerFactory) : GraphImpl(
  containerFactory,
  SubclassesIndex(containerFactory, false),
), DependencyGraph {
  private val registeredIndices = getIndices().let { list -> list.mapTo(hashSet(list.size)) { it.name } }

  override fun close() {
  }

  override fun createDelta(
    compiledSources: Iterable<NodeSource>,
    deletedSources: Iterable<NodeSource>,
    isSourceOnly: Boolean,
  ): Delta {
    val delta = if (isSourceOnly) {
      SourceOnlyDelta(registeredIndices, compiledSources, deletedSources)
    }
    else {
      DeltaImpl(compiledSources, deletedSources)
    }

    val deltaIndices = delta.indices.let { list -> list.mapTo(hashSet(list.count())) { it.name } }
    if (registeredIndices != deltaIndices) {
      throw RuntimeException("Graph delta should contain the same set of indices as the base graph\n\tCurrent graph indices: $registeredIndices\n\tCurrent Delta indices: $deltaIndices")
    }

    return delta
  }

  override fun differentiate(delta: Delta, params: DifferentiateParameters): DifferentiateResult {
    val sessionName = params.sessionName
    val deltaSources = delta.sources
    val allProcessedSources = if (delta.isSourceOnly) {
      delta.deletedSources
    }
    else {
      val all = hashSet<NodeSource>(delta.baseSources.size + delta.deletedSources.size)
      all.addAll(delta.baseSources)
      all.addAll(deltaSources)
      all.addAll(delta.deletedSources)
      all
    }
    val nodesWithErrors: Set<Node<*, *>> = if (params.isCompiledWithErrors) {
      val result = ObjectOpenCustomHashSet<Node<*, *>>(DiffCapableHashStrategy)
      for (source in delta.baseSources) {
        if (deltaSources.contains(source)) {
          result.addAll(getNodes(source))
        }
      }
      result
    }
    else {
      java.util.Set.of()
    }

    // Important: in case of errors,
    // some sources sent to recompilation ('baseSources')
    // might not have corresponding output classes either because a source has compilation errors
    // or because the compiler stopped compilation and has not managed to compile some sources
    // (=> produced no output for these sources).
    // In this case, ignore 'baseSources' when building the set of previously available nodes,
    // so that only successfully recompiled and deleted sources will take part in dependency analysis and affection of additional files.
    // This will also affect the contents of 'deletedNodes' set:
    // it will be based only on those sources
    // which were deleted or processed without errors => the current set of nodes for such files is known.
    // Nodes in the graph corresponding to those 'baseSources',
    // for which the compiler has not produced any output, are available in the 'nodeWithErrors' set and can be analyzed separately.
    val nodesBefore = ObjectOpenCustomHashSet<Node<*, *>>(DiffCapableHashStrategy)
    if (params.isCompiledWithErrors) {
      deltaSources.flatMapTo(nodesBefore) { getNodes(it) }
      delta.deletedSources.flatMapTo(nodesBefore) { getNodes(it) }
    }
    else {
      allProcessedSources.flatMapTo(nodesBefore) { getNodes(it) }
    }
    val nodesAfter = if (delta.isSourceOnly) {
      emptySet()
    }
    else {
      deltaSources.flatMapTo(ObjectOpenCustomHashSet(DiffCapableHashStrategy)) { getNodes(it) }
    }

    // Do not process 'removed' per-source file.
    // This works when a class comes from exactly one source.
    // However, it might not work, if a class can be associated with several sources
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

      val compiledSources = if (deltaSources is Set<*>) deltaSources else ObjectOpenHashSet(deltaSources.iterator())
      val deleted = deletedNodes.mapTo(hashSet(deletedNodes.size)) { it.referenceID }
      val affectedUsages = Object2ObjectOpenHashMap<Usage, Predicate<Node<*, *>>>()
      val usageQueries = ObjectOpenHashSet<Predicate<Node<*, *>>>()
      val affectedSources = ObjectOpenHashSet<NodeSource>()

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

    var incremental = true
    for (diffStrategy in differentiateStrategies) {
      if (!diffStrategy.differentiate(diffContext, nodesBefore, nodesAfter, nodesWithErrors)) {
        incremental = false
        break
      }
    }

    if (!incremental) {
      return DifferentiateResult.createNonIncremental("", params, delta, deletedNodes)
    }

    val dependingOnDeleted = diffContext.deleted.flatMapTo(hashSet()) { getDependingNodes(it) }
    val affectedSources = dependingOnDeleted.flatMapTo(hashSet()) { getSources(it) }

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

    val scopeNodes = diffContext.affectedUsages.keys.asSequence().map { it.elementOwner }.distinct()
    val candidates = scopeNodes.asSequence()
      .flatMap { getDependingNodes(it) }
      .filterTo(hashSet()) { !dependingOnDeleted.contains(it) }
    for (depSrc in candidates.asSequence().flatMap { getSources(it) }.distinct()) {
      if (!affectedSources.contains(depSrc) &&
        !diffContext.affectedSources.contains(depSrc) &&
        !allProcessedSources.contains(depSrc) &&
        params.affectionFilter().test(depSrc)) {
        var affectSource = false
        for (depNode in getNodes(depSrc).asSequence().filter { candidates.contains(it.referenceID) }) {
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

    if (delta.isSourceOnly) {
      // Some nodes may be associated with multiple sources. In this case, ensure that all these sources are sent to compilation
      val inputSources = delta.baseSources
      val deleted = delta.deletedSources
      val srcFilter = DifferentiateParameters.affectableInCurrentChunk(diffContext.getParams()).and { !deleted.contains(it) }
      for (node in (inputSources.asSequence() + deleted).flatMap { getNodes(it) }) {
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
    affectedSources.addAll(diffContext.affectedSources)

    if (!delta.isSourceOnly) {
      // complete affected file set with source-delta dependencies
      val affectedSourceDelta = createDelta(
        compiledSources = affectedSources.filterTo(ObjectOpenHashSet()) { params.belongsToCurrentCompilationChunk().test(it) },
        deletedSources = emptySet(),
        isSourceOnly = true,
      )
      val srcDeltaParams = DifferentiateParametersBuilder.create(params).compiledWithErrors(false).get()
      affectedSources.addAll(differentiate(affectedSourceDelta, srcDeltaParams).affectedSources)
    }

    return object : DifferentiateResult {
      override fun getSessionName() = sessionName

      override fun getParameters() = params

      override fun getDelta() = delta

      override fun getDeletedNodes() = deletedNodes

      override fun getAffectedSources() = affectedSources
    }
  }

  override fun integrate(diffResult: DifferentiateResult) {
    val params = diffResult.parameters
    val delta = diffResult.delta

    // handle deleted nodes and sources
    if (diffResult.deletedNodes.any()) {
      val differentiatedSources = hashSet<NodeSource>()
      if (!params.isCompiledWithErrors) {
        differentiatedSources.addAll(delta.baseSources)
      }
      differentiatedSources.addAll(delta.sources)
      differentiatedSources.addAll(delta.deletedSources)

      // the set of deleted nodes includes ones corresponding to deleted sources
      for (deletedNode in diffResult.deletedNodes) {
        @Suppress("UNCHECKED_CAST")
        val nodeSources = ObjectOpenHashSet(nodeToSourcesMap.get(deletedNode.referenceID) as Collection<NodeSource>)
        nodeSources.removeAll(differentiatedSources)
        if (nodeSources.isEmpty()) {
          nodeToSourcesMap.remove(deletedNode.referenceID)
        }
        else {
          nodeToSourcesMap.put(deletedNode.referenceID, nodeSources)
        }
      }
    }
    for (deletedSource in delta.deletedSources) {
      sourceToNodesMap.remove(deletedSource)
    }

    val updatedNodes = delta.sources.flatMapTo(ObjectOpenCustomHashSet(DiffCapableHashStrategy)) { getNodes(it) }
    for (index in indices) {
      val deltaIndex = delta.getIndex(index.name)!!
      index.integrate(diffResult.deletedNodes, updatedNodes, deltaIndex)
    }

    val visitedDeltaNodes = ObjectOpenHashSet<ReferenceID>()
    if (delta is DeltaImpl) {
      for (nodes in delta.sourceToNodesMap.values) {
        updateNodeSources(delta, nodes, visitedDeltaNodes, nodeToSourcesMap)
      }
    }
    else {
      for (deltaSource in delta.sources) {
        updateNodeSources(delta, delta.getNodes(deltaSource), visitedDeltaNodes, nodeToSourcesMap)
      }
    }

    if (delta is DeltaImpl) {
      for ((source, nodes) in delta.sourceToNodesMap.object2ObjectEntrySet().fastIterator()) {
        @Suppress("UNCHECKED_CAST")
        updateByDiff(
          map = sourceToNodesMap,
          key = source,
          dataAfter = nodes,
        ) { past, now -> deepDiffForNodes(past, now) }
      }
    }
    else {
      for (source in delta.sources) {
        @Suppress("UNCHECKED_CAST")
        updateByDiff(
          map = sourceToNodesMap,
          key = source,
          // SourceDelta returns nodes as an empty list
          dataAfter = delta.getNodes(source).let { if (it.none()) persistentHashSetOf() else it as PersistentSet<Node<*, Difference>> },
        ) { past, now -> deepDiffForNodes(past, now) }
      }
    }
  }
}

private fun deepDiffForNodes(
  past: PersistentSet<Node<*, *>>,
  now: Set<Node<*, *>>
): Difference.Specifier<Node<*, *>, Difference> {
  @Suppress("UNCHECKED_CAST")
  return deepDiffForSets(past as PersistentSet<Node<*, Difference>>, now as Set<Node<*, Difference>>) as Difference.Specifier<Node<*, *>, Difference>
}

private fun updateNodeSources(
  delta: Delta,
  nodes: Iterable<Node<*, *>>,
  visitedDeltaNodes: ObjectOpenHashSet<ReferenceID>,
  nodeToSourcesMap: MultiMaplet<ReferenceID, NodeSource>,
) {
  for (deltaNode in nodes) {
    val nodeId = deltaNode.referenceID
    if (!visitedDeltaNodes.add(nodeId)) {
      continue
    }

    @Suppress("UNCHECKED_CAST")
    val sourcesAfter = (nodeToSourcesMap.get(nodeId) as PersistentSet<NodeSource>)
      .removeAll(delta.baseSources)
      .addAll(delta.getSources(nodeId) as Collection<NodeSource>)

    updateByDiff(map = nodeToSourcesMap, key = nodeId, dataAfter = sourcesAfter) { past, now -> diffForSets(past, now) }
  }
}

private inline fun <K : Any, V : Any> updateByDiff(
  map: MultiMaplet<K, V>,
  key: K,
  dataAfter: Set<V>,
  diffComparator: (PersistentSet<V>, Set<V>) -> Difference.Specifier<V, *>,
) {
  @Suppress("UNCHECKED_CAST")
  val dataBefore = map.get(key) as PersistentSet<V>
  val beforeEmpty = dataBefore.isEmpty()
  val afterEmpty = dataAfter.isEmpty()
  if (beforeEmpty || afterEmpty) {
    if (!afterEmpty) {
      // so, before is empty
      map.put(key, dataAfter)
    }
    else if (!beforeEmpty) {
      map.remove(key)
    }
  }
  else {
    val diff = diffComparator(dataBefore, dataAfter)
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