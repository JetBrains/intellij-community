/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof.analysis

import com.intellij.diagnostic.hprof.classstore.ClassDefinition
import com.intellij.diagnostic.hprof.classstore.ClassStore
import com.intellij.diagnostic.hprof.navigator.ObjectNavigator
import com.intellij.diagnostic.hprof.util.HeapReportUtils.STRING_PADDING_FOR_COUNT
import com.intellij.diagnostic.hprof.util.HeapReportUtils.STRING_PADDING_FOR_SIZE
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toShortStringAsCount
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toShortStringAsSize
import com.intellij.diagnostic.hprof.util.RefIndexUtil
import com.intellij.diagnostic.hprof.util.TruncatingPrintBuffer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.CollectionFactory
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntIterators
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import java.util.ArrayDeque
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import kotlin.math.ceil
import kotlin.math.min

class GCRootPathsTree(
  val analysisContext: AnalysisContext,
  private val treeDisplayOptions: AnalysisConfig.TreeDisplayOptions,
  allObjectsOfClass: ClassDefinition?
) {
  companion object {
    // One batch bounds the transient memory of one walk task.
    private const val WALK_BATCH_SIZE = 1024
    // A small set registers faster without the pipeline handoff.
    private const val MINIMUM_OBJECTS_FOR_PARALLEL_WALK = 2 * WALK_BATCH_SIZE
    // The batch buffers hold the longest possible paths, so an unusually deep
    // tree configuration keeps the sequential path instead of large buffers.
    private const val MAXIMUM_TREE_DEPTH_FOR_PARALLEL_WALK = 256
    // The serial tree insertion bounds the pipeline. A few walkers saturate it.
    private val PARALLELISM = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)
  }

  private val topNode = RootNode(analysisContext.classStore)
  private var countOfIgnoredObjects = 0

  // registerObject runs once per nominated object. Reuse one map probe and one
  // single-object batch to keep the allocation rate low.
  private val probeEdge = Edge(analysisContext.classStore.classClass, 0, false)
  private val singleObjectBatch = WalkBatch(1, treeDisplayOptions.maximumTreeDepth)

  // Class lookups through the navigator seek the aux file for every path element.
  // Prefer the class cache that the traverse fills.
  private val classIndexList = analysisContext.classIndexList
  private val indexedClasses = analysisContext.indexedClasses

  // Membership checks against the disposed-IDs hash set run once per path element.
  // The shared bitmap answers each check with one array read.
  private val disposedObjectsBits = analysisContext.disposedObjectsBits

  // If all objects are of the same class and not arrays then instance size can be computed only once.
  private val objectSizeStrategy = ObjectSizeCalculationStrategy.getBestStrategyForClass(allObjectsOfClass)

  private enum class Status {
    None,
    Warning,
    LastInPath;

    companion object {
      fun getStatus(refIndex: Int, lastInPath: Boolean, disposed: Boolean): Status = when {
        lastInPath -> LastInPath
        disposed -> Warning
        refIndex == RefIndexUtil.SOFT_REFERENCE -> Warning
        refIndex == RefIndexUtil.WEAK_REFERENCE -> Warning
        else -> None
      }
    }

    override fun toString(): String = getStatusCharacter().toString()

    fun getStatusCharacter(): Char = when (this) {
      None -> ' '
      Warning -> '!'
      LastInPath -> '*'
    }
  }

  interface ObjectSizeCalculationStrategy {
    fun calculateObjectSize(nav: ObjectNavigator, id: Int): Int

    companion object {
      fun getBestStrategyForClass(classDefinition: ClassDefinition?): ObjectSizeCalculationStrategy {
        if (classDefinition == null || classDefinition.isArray()) {
          return SizeFromObjectNavigatorStrategy()
        }
        else if (classDefinition.name == "java.nio.DirectByteBuffer") {
          // When focusing on DirectByteBuffers, add sizes of native arrays.
          return DirectByteBufferNativeSizeStrategy(classDefinition)
        }
        else {
          return AllObjectsSameSizeStrategy(classDefinition.instanceSize + ClassDefinition.OBJECT_PREAMBLE_SIZE)
        }
      }
    }
  }

  private class AllObjectsSameSizeStrategy(size: Int) : ObjectSizeCalculationStrategy {

    private val objectSize = size

    override fun calculateObjectSize(nav: ObjectNavigator, id: Int): Int = objectSize
  }

  private class SizeFromObjectNavigatorStrategy : ObjectSizeCalculationStrategy {
    override fun calculateObjectSize(nav: ObjectNavigator, id: Int): Int {
      nav.goTo(id.toLong(), ObjectNavigator.ReferenceResolution.NO_REFERENCES)
      return nav.getObjectSize()
    }
  }

  private class DirectByteBufferNativeSizeStrategy(private val classDefinition: ClassDefinition) : ObjectSizeCalculationStrategy {
    init {
      assert(classDefinition.name == "java.nio.DirectByteBuffer")
    }

    override fun calculateObjectSize(nav: ObjectNavigator, id: Int): Int {
      nav.goTo(id.toLong(), ObjectNavigator.ReferenceResolution.ALL_REFERENCES)
      assert(nav.getClass() == classDefinition)
      return nav.getExtraData() + nav.getObjectSize()
    }
  }

  fun registerObject(objectId: Int) {
    registerObject(objectId, objectSizeStrategy)
  }

  // Reuses one size strategy for all instances of the specified class.
  fun registerObjectsOfClass(objectIds: IntSet, classDefinition: ClassDefinition) {
    val classObjectSizeStrategy =
      ObjectSizeCalculationStrategy.getBestStrategyForClass(classDefinition)
    if (objectIds.size >= MINIMUM_OBJECTS_FOR_PARALLEL_WALK && PARALLELISM >= 2 &&
        treeDisplayOptions.maximumTreeDepth <= MAXIMUM_TREE_DEPTH_FOR_PARALLEL_WALK) {
      registerObjectsWithParallelWalk(objectIds, classObjectSizeStrategy)
      return
    }
    objectIds.forEach { objectId ->
      registerObject(objectId, classObjectSizeStrategy)
    }
  }

  // The reusable buffers of one walked batch, in registration order.
  // An empty path range marks an object with a too-deep GC-root path.
  // Element order in a path is from the object to its root. The root element
  // carries RefIndexUtil.ROOT. A zero class index means "resolve through the
  // navigator on the registration thread".
  private class WalkBatch(capacity: Int, maxTreeDepth: Int) {
    val objectIds: IntArray = IntArray(capacity)
    var objectCount: Int = 0
    val pathOffsets: IntArray = IntArray(capacity + 1)
    private val maxElements = capacity * (maxTreeDepth + 1)
    val elementIds: IntArray = IntArray(maxElements)
    val elementRefs: ByteArray = ByteArray(maxElements)
    val elementSizes: IntArray = IntArray(maxElements)
    val elementClassIndexes: IntArray = IntArray(maxElements)
    val elementDisposed: BooleanArray = BooleanArray(maxElements)
  }

  // Walks the parent chains of one batch. The method reads only the traverse
  // output lists and the disposed bitmap, so worker threads can run it.
  private fun walkChains(batch: WalkBatch): WalkBatch {
    val parentMapping = analysisContext.parentList
    val refIndexMapping = analysisContext.refIndexList
    val sizesMapping = analysisContext.sizesList
    val classIndexList = classIndexList
    val maxTreeDepth = treeDisplayOptions.maximumTreeDepth

    val objectIds = batch.objectIds
    val count = batch.objectCount
    val pathOffsets = batch.pathOffsets
    val ids = batch.elementIds
    val refs = batch.elementRefs
    val sizes = batch.elementSizes
    val classIndexes = batch.elementClassIndexes
    val disposed = batch.elementDisposed
    var cursor = 0

    for (i in 0 until count) {
      val start = cursor
      var objectIterationId = objectIds[i]
      var parentId = parentMapping[objectIterationId]
      var depth = 0
      while (depth < maxTreeDepth && parentId != objectIterationId) {
        ids[cursor] = objectIterationId
        refs[cursor] = refIndexMapping[objectIterationId].toByte()
        sizes[cursor] = sizesMapping[objectIterationId]
        cursor++
        objectIterationId = parentId
        parentId = parentMapping[objectIterationId]
        depth++
      }
      if (parentId != objectIterationId) {
        // Too-deep path: drop the partial chain and keep the range empty.
        cursor = start
      }
      else {
        ids[cursor] = objectIterationId
        refs[cursor] = RefIndexUtil.ROOT.toByte()
        sizes[cursor] = sizesMapping[objectIterationId]
        cursor++
      }
      for (e in start until cursor) {
        val id = ids[e]
        classIndexes[e] = classIndexList?.get(id) ?: 0
        disposed[e] = disposedObjectsBits.get(id)
      }
      pathOffsets[i + 1] = cursor
    }
    return batch
  }

  private fun classForWalkedElement(batch: WalkBatch, element: Int): ClassDefinition {
    val classIndex = batch.elementClassIndexes[element]
    if (classIndex != 0) {
      return indexedClasses[classIndex - 1]
    }
    return analysisContext.navigator.getClassForObjectId(batch.elementIds[element].toLong())
  }

  // Inserts one walked batch into the tree. Only the registration thread calls
  // this method, in batch submission order, so the tree grows exactly as with
  // the sequential registration.
  private fun insertWalkedBatch(batch: WalkBatch,
                                objectSizeStrategy: ObjectSizeCalculationStrategy) {
    val nav = analysisContext.navigator
    for (i in 0 until batch.objectCount) {
      val start = batch.pathOffsets[i]
      val end = batch.pathOffsets[i + 1]
      if (start == end) {
        countOfIgnoredObjects++
        continue
      }
      val size = objectSizeStrategy.calculateObjectSize(nav, batch.objectIds[i])
      val rootIndex = end - 1
      var currentNode = topNode.addEdge(
        batch.elementIds[rootIndex], size, batch.elementSizes[rootIndex], classForWalkedElement(batch, rootIndex),
        batch.elementRefs[rootIndex], batch.elementDisposed[rootIndex])
      for (e in rootIndex - 1 downTo start) {
        probeEdge.classDefinition = classForWalkedElement(batch, e)
        probeEdge.refIndex = batch.elementRefs[e]
        probeEdge.disposed = batch.elementDisposed[e]
        currentNode = currentNode.addEdge(batch.elementIds[e], size, batch.elementSizes[e], probeEdge)
      }
    }
  }

  // Splits the read-only chain walks over worker threads and applies the tree
  // insertions on this thread in submission order. The order and the report
  // stay identical to the sequential registration. The lists of the analysis
  // context must support concurrent reads.
  private fun registerObjectsWithParallelWalk(objectIds: IntSet,
                                              objectSizeStrategy: ObjectSizeCalculationStrategy) {
    val executor = AppExecutorUtil.getAppExecutorService()
    val maxPendingBatches = PARALLELISM + 1
    val batchPool = ArrayDeque<WalkBatch>()
    repeat(maxPendingBatches) { batchPool.add(WalkBatch(WALK_BATCH_SIZE, treeDisplayOptions.maximumTreeDepth)) }
    val pending = ArrayDeque<Future<WalkBatch>>()
    val iterator = objectIds.iterator()
    try {
      while (true) {
        while (pending.size < maxPendingBatches && iterator.hasNext()) {
          val batch = batchPool.removeFirst()
          batch.objectCount = IntIterators.unwrap(iterator, batch.objectIds)
          pending.addLast(executor.submit(Callable { walkChains(batch) }))
        }
        val future = pending.pollFirst() ?: break
        val batch = future.get()
        insertWalkedBatch(batch, objectSizeStrategy)
        batchPool.addLast(batch)
      }
    }
    catch (e: ExecutionException) {
      throw e.cause ?: e
    }
    finally {
      // Cancel the queued walks and wait for the running ones, so no worker
      // reads the mapped lists after this method returns.
      pending.forEach { it.cancel(false); runCatching { it.get() } }
    }
  }

  private fun registerObject(objectId: Int,
                             objectSizeStrategy: ObjectSizeCalculationStrategy) {
    singleObjectBatch.objectIds[0] = objectId
    singleObjectBatch.objectCount = 1
    insertWalkedBatch(walkChains(singleObjectBatch), objectSizeStrategy)
  }

  fun printTree(): String {
    val result = StringBuilder()
    if (countOfIgnoredObjects > 0) {
      result.append("Ignored ${countOfIgnoredObjects} too-deep objects\n")
    }
    val rootReasonGetter = { id: Int ->
      (analysisContext.navigator.getRootReasonForObjectId(id.toLong())?.description ?: "<Couldn't find root description>")
    }
    result.append(topNode.createHotPathReport(treeDisplayOptions, rootReasonGetter))
    return result.toString()
  }

  fun getDisposedDominatorNodes(): Map<ClassDefinition, List<RegularNode>> {
    val result = HashMap<ClassDefinition, MutableList<RegularNode>>()
    topNode.collectDisposedDominatorNodes(result)
    return result
  }

  // Registration reuses one probe instance for map lookups, so the fields are mutable.
  // A stored key must never change: addEdge copies the probe before an insert.
  class Edge(var classDefinition: ClassDefinition, var refIndex: Byte, var disposed: Boolean) {
    // The class store canonicalizes ClassDefinition instances, so identity equality is enough.
    // The map probes run once per path element, hundreds of millions of times on a large dump.
    override fun equals(other: Any?): Boolean =
      other is Edge && classDefinition === other.classDefinition && refIndex == other.refIndex && disposed == other.disposed

    override fun hashCode(): Int = (classDefinition.hashCode() * 31 + refIndex) * 31 + if (disposed) 1 else 0

    fun copy(): Edge = Edge(classDefinition, refIndex, disposed)
  }

  class RegularNode {

    // In regular nodes paths are grouped by class definition
    var edges: MutableMap<Edge, RegularNode>? = null
    var pathsCount: Int = 0
    var pathsSize: Int = 0
    var totalSizeInDwords: Int = 0
    val instances: IntOpenHashSet = IntOpenHashSet(1)

    fun addEdge(objectId: Int,
                objectSize: Int,
                subgraphSizeInDwords: Int,
                probe: Edge): RegularNode {
      var localEdges = edges
      if (localEdges == null) {
        localEdges = CollectionFactory.createSmallMemoryFootprintMap(1)
        edges = localEdges
      }
      var node = localEdges[probe]
      if (node == null) {
        node = RegularNode()
        localEdges[probe.copy()] = node
      }
      node.pathsCount++
      if (node.pathsSize + objectSize.toLong() > Int.MAX_VALUE) {
        node.pathsSize = Int.MAX_VALUE
      }
      else {
        node.pathsSize += objectSize
      }

      val added = node.instances.add(objectId)
      if (added) {
        if (node.totalSizeInDwords + subgraphSizeInDwords.toLong() > Int.MAX_VALUE) {
          node.totalSizeInDwords = Int.MAX_VALUE
        }
        else {
          node.totalSizeInDwords += subgraphSizeInDwords
        }
      }
      return node
    }

    fun collectDisposedDominatorNodes(result: MutableMap<ClassDefinition, MutableList<RegularNode>>) {
      val stack = ArrayDeque<RegularNode>()
      stack.push(this)
      while (stack.isNotEmpty()) {
        val currentNode = stack.pop()
        currentNode.edges?.forEach { (edge, childNode) ->
          if (edge.disposed) {
            result.getOrPut(edge.classDefinition) { mutableListOf() }.add(childNode)
          }
          else {
            stack.push(childNode)
          }
        }
      }
    }
  }

  class RootNode(private val classStore: ClassStore) {
    // In root node each instance has a separate path
    val edges: Int2ObjectOpenHashMap<Pair<RegularNode, Edge>> = Int2ObjectOpenHashMap<Pair<RegularNode, Edge>>()

    fun addEdge(objectId: Int,
                objectSize: Int,
                subgraphSizeInDwords: Int,
                classDefinition: ClassDefinition,
                refIndex: Byte,
                disposed: Boolean): RegularNode {
      val nullableNode = edges.get(objectId)?.first
      val node: RegularNode

      if (nullableNode != null) {
        node = nullableNode
      }
      else {
        val newNode = RegularNode()
        val pair = Pair(newNode, Edge(classDefinition, refIndex, disposed))
        newNode.instances.add(objectId)
        edges.put(objectId, pair)
        node = newNode
        node.totalSizeInDwords = subgraphSizeInDwords
      }
      node.pathsCount++
      if (node.pathsSize + objectSize.toLong() > Int.MAX_VALUE) {
        node.pathsSize = Int.MAX_VALUE
      }
      else {
        node.pathsSize += objectSize
      }

      return node
    }

    private fun calculateTotalInstanceCount(): Int {
      var result = 0
      for (node in edges.values) {
        result += node.first.pathsCount
      }
      return result
    }

    data class StackEntry(
      val parentClass: ClassDefinition?,
      val edge: Edge,
      val node: RegularNode,
      val indent: String,
      val nextIndent: String
    )

    // Selects the legacy or merged-root report format requested by the analysis.
    fun createHotPathReport(treeDisplayOptions: AnalysisConfig.TreeDisplayOptions,
                            rootReasonGetter: (Int) -> String): String =
      if (treeDisplayOptions.useMergedNominatedClassesReport) {
        createMergedRootPathsReport(treeDisplayOptions, rootReasonGetter)
      }
      else {
        createLegacyHotPathReport(treeDisplayOptions, rootReasonGetter)
      }

    // Produces the original nominated-object-driven hot-path report.
    private fun createLegacyHotPathReport(treeDisplayOptions: AnalysisConfig.TreeDisplayOptions,
                                          rootReasonGetter: (Int) -> String): String {
      val rootList = mutableListOf<Triple<Int, RegularNode, Edge>>()
      val result = StringBuilder()
      val printFunc = { s: String -> result.appendLine(s); Unit }

      for (entry in edges.int2ObjectEntrySet().fastIterator()) {
        rootList.add(Triple(entry.intKey, entry.value.first, entry.value.second))
      }
      val totalInstanceCount = calculateTotalInstanceCount()

      val minimumObjectsForReport = min(
        treeDisplayOptions.minimumObjectCount,
        (ceil(totalInstanceCount / 100.0) * treeDisplayOptions.minimumObjectCountPercent).toInt())

      // Show paths from roots that have at least minimumObjectCountPercent%, minimumObjectCount objects or size of all reported objects
      // in the subtree is more than minimumObjectSize.
      // Always show at least two paths.
      rootList
        .sortedByDescending { it.second.totalSizeInDwords }
        .filterIndexed { index, (_, node, _) ->
          index < treeDisplayOptions.minimumPaths ||
          node.pathsCount >= minimumObjectsForReport ||
          node.pathsSize >= treeDisplayOptions.minimumObjectSize
        }
        .forEachIndexed { index, (rootObjectId, rootNode, rootEdge) ->
          val rootReasonString = rootReasonGetter(rootObjectId)
          val rootPercent = (100.0 * rootNode.pathsCount / totalInstanceCount).toInt()

          result.appendLine("Root ${index + 1}:")
          printReportLine(printFunc,
                          treeDisplayOptions,
                          rootNode.pathsCount,
                          rootPercent,
                          rootNode.pathsSize,
                          rootNode.totalSizeInDwords.toLong() * 4,
                          1,
                          Status.getStatus(RefIndexUtil.ROOT, false, false),
                          false,
                          null,
                          "",
                          "ROOT: $rootReasonString")

          TruncatingPrintBuffer(treeDisplayOptions.headLimit, treeDisplayOptions.tailLimit, printFunc).use { buffer ->
            // Iterate over the hot path
            val stack = ArrayDeque<StackEntry>()
            stack.push(StackEntry(null, rootEdge, rootNode, "", ""))

            while (!stack.isEmpty()) {
              val (parentClass, edge, node, indent, nextIndent) = stack.pop()
              val classDefinition = edge.classDefinition
              val disposed = edge.disposed
              val refIndex = java.lang.Byte.toUnsignedInt(edge.refIndex)

              printReportLine(buffer::println,
                              treeDisplayOptions,
                              node.pathsCount,
                              (100.0 * node.pathsCount / totalInstanceCount).toInt(),
                              node.pathsSize,
                              node.totalSizeInDwords.toLong() * 4,
                              node.instances.size,
                              Status.getStatus(refIndex, node.edges == null, disposed),
                              disposed,
                              RefIndexUtil.getFieldDescription(refIndex, parentClass, classStore),
                              indent,
                              classDefinition.prettyName)

              val currentNodeEdges = node.edges ?: continue
              val childrenToReport =
                currentNodeEdges
                  .entries
                  .sortedWith(::compareRegularNodes)
                  .filterIndexed { index, e ->
                    index == 0 ||
                    e.value.pathsCount >= minimumObjectsForReport ||
                    e.value.pathsSize >= treeDisplayOptions.minimumObjectSize ||
                    e.value.totalSizeInDwords.toLong() * 4 >= treeDisplayOptions.minimumSubgraphSize
                  }
                  .asReversed()

              if (childrenToReport.size == 1 && treeDisplayOptions.smartIndent) {
                // No indentation for a single child
                stack.push(StackEntry(classDefinition, childrenToReport[0].key, childrenToReport[0].value, nextIndent, nextIndent))
              }
              else {
                // Don't report too deep paths
                if (nextIndent.length >= treeDisplayOptions.maximumIndent)
                  printReportLine(buffer::println,
                                  treeDisplayOptions,
                                  null, null, null, null,
                                  null, Status.LastInPath,  null, null,
                                  nextIndent, "\\-[...]")
                else {
                  // Add indentation only if there are 2+ children
                  childrenToReport.forEachIndexed { index, e ->
                    if (index == 0) stack.push(StackEntry(classDefinition, e.key, e.value, "$nextIndent\\-", "$nextIndent  "))
                    else stack.push(StackEntry(classDefinition, e.key, e.value, "$nextIndent+-", "$nextIndent| "))
                  }
                }
              }
            }
          }
        }
      return result.toString()
    }

    private object MergedReportLimits {
      // Keep non-mandatory roots whose deep size is at least this percentage of the largest root.
      const val MINIMUM_ROOT_PERCENT = 5
      // Bound the number of roots in the merged report.
      const val MAXIMUM_ROOT_COUNT = 15
      // Guarantee this many nodes to every reported root.
      const val FIXED_TREE_NODE_BUDGET_PER_ROOT = 100
      // Add up to this many nodes according to the root's deep size relative to the largest root.
      const val PROPORTIONAL_TREE_NODE_BUDGET_BASIS = 150
      // Use this relative deep-size floor for the largest root.
      const val SUBGRAPH_PERCENT_FOR_LARGEST_ROOT = 1.0
      // Increase the floor continuously to this value for roots at or below the minimum root size.
      const val SUBGRAPH_PERCENT_AT_MINIMUM_ROOT_SIZE = 5.0
      // Keep the subgraph floor meaningful for small roots.
      const val MINIMUM_SUBGRAPH_SIZE_BYTES = 10L * 1024 * 1024
      // Stop adding children after they represent this percentage of their parent.
      const val MINIMUM_LOCAL_CHILDREN_SIZE_PERCENT = 85
      // Bound the fan-out of every reported node.
      const val MAXIMUM_LOCAL_CHILD_COUNT = 6
      // Continue the largest-child chain briefly after it falls below the size floor.
      const val CONTEXT_NODES_AFTER_SIZE_THRESHOLD = 3
    }

    private class TreeNodeBudget(private var remaining: Int) {
      // Reserves one tree-node line if the root's output budget is not exhausted.
      fun tryConsume(): Boolean {
        if (remaining == 0) return false
        remaining--
        return true
      }
    }

    // Selects significant roots and prints a deep-size-focused subtree for each of them.
    private fun createMergedRootPathsReport(
      treeDisplayOptions: AnalysisConfig.TreeDisplayOptions,
      rootReasonGetter: (Int) -> String,
    ): String {
      val result = StringBuilder()
      val printFunc = { s: String -> result.appendLine(s); Unit }

      val rootList = ArrayList<Triple<Int, Edge, RegularNode>>(edges.size)
      for (entry in edges.int2ObjectEntrySet().fastIterator()) {
        rootList.add(Triple(entry.intKey, entry.value.second, entry.value.first))
      }
      val totalInstanceCount = calculateTotalInstanceCount()
      val sortedRoots = rootList.sortedWith(
        compareByDescending<Triple<Int, Edge, RegularNode>> { it.third.totalSizeInDwords }
          .thenBy { it.first }
      )
      val largestRootSize = sortedRoots.firstOrNull()?.third?.totalSizeInDwords?.toLong() ?: 0L
      // Absolute limits hide useful roots in small dumps. Compare all roots with the largest root instead.
      val rootsToReport = sortedRoots.asSequence().filterIndexed { index, (_, _, node) ->
        index < treeDisplayOptions.minimumPaths ||
        node.totalSizeInDwords.toLong() * 100 >= largestRootSize * MergedReportLimits.MINIMUM_ROOT_PERCENT
      }.take(MergedReportLimits.MAXIMUM_ROOT_COUNT)
        .toList()

      rootsToReport.forEachIndexed { index, (rootObjectId, rootEdge, rootNode) ->
        val treeNodeBudget = TreeNodeBudget(calculateRootTreeNodeBudget(rootNode, largestRootSize))
        // The synthetic ROOT line is a tree node; Root N: remains outside the quota.
        if (!treeNodeBudget.tryConsume()) return@forEachIndexed

        val rootReasonString = rootReasonGetter(rootObjectId)
        val rootPercent = (100.0 * rootNode.pathsCount / totalInstanceCount).toInt()

        result.appendLine("Root ${index + 1}:")
        printReportLine(printFunc,
                        treeDisplayOptions,
                        rootNode.pathsCount,
                        rootPercent,
                        rootNode.pathsSize,
                        rootNode.totalSizeInDwords.toLong() * 4,
                        1,
                        Status.getStatus(RefIndexUtil.ROOT, false, false),
                        false,
                        null,
                        "",
                        "ROOT: $rootReasonString")

        val minimumSignificantSubgraphSize =
          calculateMinimumSignificantSubgraphSize(rootNode, largestRootSize)
        val truncated = printSignificantRootBody(printFunc,
                                                 rootEdge,
                                                 rootNode,
                                                 treeDisplayOptions,
                                                 totalInstanceCount,
                                                 minimumSignificantSubgraphSize,
                                                 treeNodeBudget)
        if (truncated) {
          result.appendLine("[...truncated...]")
        }
      }
      return result.toString()
    }

    // Combines a fixed per-root node allowance with a part proportional to the largest root.
    private fun calculateRootTreeNodeBudget(root: RegularNode, largestRootSize: Long): Int {
      val proportionalBudget =
        if (largestRootSize == 0L) MergedReportLimits.PROPORTIONAL_TREE_NODE_BUDGET_BASIS
        else (MergedReportLimits.PROPORTIONAL_TREE_NODE_BUDGET_BASIS.toLong() *
              root.totalSizeInDwords.toLong() / largestRootSize).toInt()
      return MergedReportLimits.FIXED_TREE_NODE_BUDGET_PER_ROOT + proportionalBudget
    }

    // Calculates the root-specific deep-size floor used to recognize significant child subgraphs.
    private fun calculateMinimumSignificantSubgraphSize(root: RegularNode, largestRootSize: Long): Long {
      val minimumRootSizeRatio = MergedReportLimits.MINIMUM_ROOT_PERCENT / 100.0
      val rootSizeRatio =
        if (largestRootSize == 0L) 1.0
        else (root.totalSizeInDwords.toDouble() / largestRootSize.toDouble()).coerceIn(minimumRootSizeRatio, 1.0)
      val interpolation = (1.0 - rootSizeRatio) / (1.0 - minimumRootSizeRatio)
      val relativeSubgraphPercent =
        MergedReportLimits.SUBGRAPH_PERCENT_FOR_LARGEST_ROOT +
        (MergedReportLimits.SUBGRAPH_PERCENT_AT_MINIMUM_ROOT_SIZE -
         MergedReportLimits.SUBGRAPH_PERCENT_FOR_LARGEST_ROOT) * interpolation
      val rootSizeInBytes = root.totalSizeInDwords.toLong() * 4
      return maxOf(MergedReportLimits.MINIMUM_SUBGRAPH_SIZE_BYTES,
                   (rootSizeInBytes.toDouble() * relativeSubgraphPercent / 100.0).toLong())
    }

    // Prints one root's selected subtree and reports whether its node budget truncated the output.
    private fun printSignificantRootBody(printFunc: (String) -> Any,
                                         rootEdge: Edge,
                                         rootNode: RegularNode,
                                         treeDisplayOptions: AnalysisConfig.TreeDisplayOptions,
                                         totalInstanceCount: Int,
                                         minimumSignificantSubgraphSize: Long,
                                         treeNodeBudget: TreeNodeBudget): Boolean {
      val stack = ArrayDeque<Pair<StackEntry, Int?>>()
      stack.push(StackEntry(null, rootEdge, rootNode, "", "") to null)

      while (stack.isNotEmpty()) {
        // Only report truncation when another tree node actually remains.
        if (!treeNodeBudget.tryConsume()) return true

        val (stackEntry, insignificantContextNodesRemaining) = stack.pop()
        val (parentClass, edge, node, indent, nextIndent) = stackEntry
        val refIndex = java.lang.Byte.toUnsignedInt(edge.refIndex)
        val repeatedFieldName = RefIndexUtil.getFieldDescription(refIndex, parentClass, classStore)
        var chainEndEdge = edge
        var chainEndNode = node
        var collapsedNodeCount = 1
        var childrenToReport = selectChildrenToReport(chainEndNode,
                                                      minimumSignificantSubgraphSize,
                                                      insignificantContextNodesRemaining)

        // Collapse only a chain with one selected child and the same field, referenced class and disposed state.
        // Keep the first node's statistics, but use the chain end's status and children.
        if (repeatedFieldName != null) {
          while (true) {
            val child = if (insignificantContextNodesRemaining == 0) {
              // No following context node is displayed, but a matching hidden tail still contributes to this collapsed node and its status.
              getSortedChildren(chainEndNode).firstOrNull() ?: break
            }
            else {
              val (selectedChild, childContextNodesRemaining) = childrenToReport.singleOrNull() ?: break
              // Keep the size-threshold boundary visible on the last significant node.
              if (insignificantContextNodesRemaining == null && childContextNodesRemaining != null) break
              selectedChild
            }
            val childEdge = child.key
            val childRefIndex = java.lang.Byte.toUnsignedInt(childEdge.refIndex)
            val childFieldName =
              RefIndexUtil.getFieldDescription(childRefIndex, chainEndEdge.classDefinition, classStore)
            if (childFieldName != repeatedFieldName ||
                childEdge.classDefinition != edge.classDefinition ||
                childEdge.disposed != edge.disposed) {
              break
            }

            chainEndEdge = childEdge
            chainEndNode = child.value
            collapsedNodeCount++
            childrenToReport = selectChildrenToReport(chainEndNode,
                                                      minimumSignificantSubgraphSize,
                                                      insignificantContextNodesRemaining)
          }
        }

        val displayedFieldName = repeatedFieldName?.let {
          if (collapsedNodeCount == 1) it else "$collapsedNodeCount*$it"
        }

        printReportLine(printFunc,
                        treeDisplayOptions,
                        node.pathsCount,
                        (100.0 * node.pathsCount / totalInstanceCount).toInt(),
                        node.pathsSize,
                        node.totalSizeInDwords.toLong() * 4,
                        node.instances.size,
                        Status.getStatus(java.lang.Byte.toUnsignedInt(chainEndEdge.refIndex),
                                         chainEndNode.edges.isNullOrEmpty(),
                                         edge.disposed),
                        edge.disposed,
                        displayedFieldName,
                        indent,
                        edge.classDefinition.prettyName)

        if (childrenToReport.isEmpty()) continue
        if (childrenToReport.size == 1 && treeDisplayOptions.smartIndent) {
          // Keep every node in a single-child chain, but do not add visual indentation for it.
          val (child, childContextNodesRemaining) = childrenToReport[0]
          stack.push(StackEntry(chainEndEdge.classDefinition,
                                child.key,
                                child.value,
                                nextIndent,
                                nextIndent) to childContextNodesRemaining)
        }
        else {
          // Don't report too deep paths.
          if (nextIndent.length >= treeDisplayOptions.maximumIndent) {
            if (!treeNodeBudget.tryConsume()) return true

            printReportLine(printFunc,
                            treeDisplayOptions,
                            null, null, null, null,
                            null, Status.LastInPath, null, null,
                            nextIndent, "\\-[...]")
          }
          else {
            // Add indentation only if there are 2+ children.
            childrenToReport.forEachIndexed { index, (child, childContextNodesRemaining) ->
              if (index == 0) {
                stack.push(StackEntry(chainEndEdge.classDefinition,
                                      child.key,
                                      child.value,
                                      "$nextIndent\\-",
                                      "$nextIndent  ") to childContextNodesRemaining)
              }
              else {
                stack.push(StackEntry(chainEndEdge.classDefinition,
                                      child.key,
                                      child.value,
                                      "$nextIndent+-",
                                      "$nextIndent| ") to childContextNodesRemaining)
              }
            }
          }
        }
      }
      return false
    }

    // Selects children according to whether traversal is still significant or is showing below-floor context.
    private fun selectChildrenToReport(node: RegularNode,
                                       minimumSignificantSubgraphSize: Long,
                                       insignificantContextNodesRemaining: Int?): List<Pair<Map.Entry<Edge, RegularNode>, Int?>> {
      if (insignificantContextNodesRemaining == 0) return emptyList()

      val sortedChildren = getSortedChildren(node)
      if (sortedChildren.isEmpty()) return emptyList()

      return if (insignificantContextNodesRemaining != null) {
        listOf(sortedChildren.first() to insignificantContextNodesRemaining - 1)
      }
      else {
        selectInitialChildrenToReport(node, minimumSignificantSubgraphSize, sortedChildren)
      }
    }

    // Selects above-floor children up to local coverage limits, or starts largest-child context.
    private fun selectInitialChildrenToReport(node: RegularNode,
                                              minimumSignificantSubgraphSize: Long,
                                              sortedChildren: List<Map.Entry<Edge, RegularNode>>): List<Pair<Map.Entry<Edge, RegularNode>, Int?>> {
      if (sortedChildren.isEmpty()) return emptyList()

      val globallySignificantChildren =
        sortedChildren.filter { it.value.totalSizeInDwords.toLong() * 4 >= minimumSignificantSubgraphSize }
      val significantChildren = mutableListOf<Map.Entry<Edge, RegularNode>>()
      var selectedChildrenSize = 0L
      for (child in globallySignificantChildren) {
        significantChildren.add(child)
        selectedChildrenSize += child.value.totalSizeInDwords
        // Prefer enough largest children to represent the parent, but keep a hard fan-out limit.
        if (significantChildren.size >= MergedReportLimits.MAXIMUM_LOCAL_CHILD_COUNT ||
            selectedChildrenSize * 100 >=
            node.totalSizeInDwords.toLong() * MergedReportLimits.MINIMUM_LOCAL_CHILDREN_SIZE_PERCENT) {
          break
        }
      }

      return if (significantChildren.isNotEmpty()) {
        significantChildren.map { it to null }.asReversed()
      }
      else {
        listOf(sortedChildren.first() to MergedReportLimits.CONTEXT_NODES_AFTER_SIZE_THRESHOLD - 1)
      }
    }

    // Returns children in deterministic descending significance order.
    private fun getSortedChildren(node: RegularNode): List<Map.Entry<Edge, RegularNode>> =
      node.edges?.entries?.sortedWith(::compareRegularNodes) ?: emptyList()

    // Orders children by deep size, path size, reference index, and class name.
    private fun compareRegularNodes(first: Map.Entry<Edge, RegularNode>,
                                    second: Map.Entry<Edge, RegularNode>): Int {
      val compareByTotalSizeDesc = second.value.totalSizeInDwords.compareTo(first.value.totalSizeInDwords)
      if (compareByTotalSizeDesc != 0) return compareByTotalSizeDesc

      val compareByPathsSizeDesc = second.value.pathsSize.compareTo(first.value.pathsSize)
      if (compareByPathsSizeDesc != 0) return compareByPathsSizeDesc

      val compareByRefIndex = first.key.refIndex.compareTo(second.key.refIndex)
      if (compareByRefIndex != 0) return compareByRefIndex

      return first.key.classDefinition.name.compareTo(second.key.classDefinition.name)
    }

    private fun printReportLine(printFunc: (String) -> Any,
                                treeDisplayOptions: AnalysisConfig.TreeDisplayOptions,
                                pathsCount: Int?,
                                percent: Int?,
                                instanceSize: Int?,
                                subgraphSize: Long?,
                                instanceCount: Int?,
                                status: Status,
                                disposed: Boolean?,
                                fieldName: String?,
                                indent: String,
                                text: String) {
      val pathsCountString = (pathsCount?.let { toShortStringAsCount(it.toLong()) } ?: "").padStart(STRING_PADDING_FOR_COUNT)
      val percentString = (percent?.let { "$it%" } ?: "").padStart(4)
      val instanceSizeString = (instanceSize?.let { toShortStringAsSize(it.toLong()) } ?: "").padStart(STRING_PADDING_FOR_SIZE)
      val instanceCountString = (instanceCount ?: "").toString().padStart(10)
      val fieldNameString = if (fieldName != null) "$fieldName: " else ""
      val disposedString = if (disposed == true) " (disposed)" else ""
      val subgraphSizeString = (subgraphSize?.let { toShortStringAsSize(it) } ?: "").padStart(STRING_PADDING_FOR_SIZE)

      if (treeDisplayOptions.showSize) {
        printFunc(
          "[$pathsCountString/$percentString/$instanceSizeString] $subgraphSizeString $instanceCountString $status $indent$fieldNameString$text$disposedString")
      }
      else {
        printFunc("$status $indent$fieldNameString$text$disposedString")
      }
    }

    fun collectDisposedDominatorNodes(result: MutableMap<ClassDefinition, MutableList<RegularNode>>) {
      for (value in edges.values) {
        value.first.collectDisposedDominatorNodes(result)
      }
    }
  }
}
