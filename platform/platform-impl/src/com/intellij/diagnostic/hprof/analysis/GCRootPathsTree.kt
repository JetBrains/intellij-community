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
import com.intellij.diagnostic.hprof.classstore.InstanceField
import com.intellij.diagnostic.hprof.navigator.ObjectNavigator
import com.intellij.diagnostic.hprof.util.HeapReportUtils.STRING_PADDING_FOR_COUNT
import com.intellij.diagnostic.hprof.util.HeapReportUtils.STRING_PADDING_FOR_SIZE
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toShortStringAsCount
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toShortStringAsSize
import com.intellij.diagnostic.hprof.util.TruncatingPrintBuffer
import gnu.trove.TIntArrayList
import gnu.trove.TIntHashSet
import gnu.trove.TIntObjectHashMap
import java.util.*

class GCRootPathsTree(
  val analysisContext: AnalysisContext,
  val treeDisplayOptions: AnalysisConfig.TreeDisplayOptions,
  allObjectsOfClass: ClassDefinition?
) {
  private val topNode = RootNode(analysisContext.classStore)
  private var countOfIgnoredObjects = 0

  // If all objects are of the same class and not arrays then instance size can be computed only once.
  private val objectSizeStrategy = ObjectSizeCalculationStrategy.getBestStrategyForClass(allObjectsOfClass)

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
    val nav = analysisContext.navigator
    val parentMapping = analysisContext.parentList
    val refIndexMapping = analysisContext.refIndexList
    val sizesMapping = analysisContext.sizesList
    val disposedObjectsIDsSet = analysisContext.disposedObjectsIDs

    val gcPath = TIntArrayList()
    val fieldsPath = TIntArrayList()
    var objectIterationId = objectId
    var parentId = parentMapping[objectIterationId]
    var count = 0

    fieldsPath.add(0)
    val maxTreeDepth = treeDisplayOptions.maximumTreeDepth
    while (count < maxTreeDepth && parentId != objectIterationId) {
      gcPath.add(objectIterationId)
      if (treeDisplayOptions.showFieldNames) {
        fieldsPath.add(refIndexMapping[objectIterationId])
      }
      else {
        fieldsPath.add(0)
      }
      objectIterationId = parentId
      parentId = parentMapping[objectIterationId]
      count++
    }

    if (parentId != objectIterationId) {
      // Object ignored as its GC-root path is too long
      countOfIgnoredObjects++
      return
    }

    gcPath.add(objectIterationId)

    assert(gcPath.size() == fieldsPath.size())

    val size = objectSizeStrategy.calculateObjectSize(nav, objectId)

    var currentNode: Node = topNode
    for (i in gcPath.size() - 1 downTo 0) {
      val id = gcPath[i]
      var classDefinition = nav.getClassForObjectId(id.toLong())
      var fieldName: String? = null
      if (fieldsPath[i] != 0) {
        if (classDefinition.name == "java.lang.Class") {
          classDefinition = nav.classStore[id.toLong()]
          fieldName = classDefinition.getClassFieldName(fieldsPath[i] - 1)
        }
        else {
          fieldName = classDefinition.getRefField(nav.classStore, fieldsPath[i] - 1).name
        }
      }
      currentNode = currentNode.addEdge(id, size, sizesMapping[id], classDefinition, fieldName, disposedObjectsIDsSet.contains(id))
    }
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

  interface Node {
    fun addEdge(objectId: Int,
                objectSize: Int,
                subgraphSizeInDwords: Int,
                classDefinition: ClassDefinition,
                fieldName: String?,
                disposed: Boolean): Node
  }

  data class Edge(val classDefinition: ClassDefinition, val fieldName: String?, val disposed: Boolean)

  class RegularNode : Node {

    // In regular nodes paths are grouped by class definition
    var edges: HashMap<Edge, RegularNode>? = null
    var pathsCount = 0
    var pathsSize = 0
    var totalSizeInDwords = 0
    val instances = TIntHashSet(1)

    override fun addEdge(objectId: Int,
                         objectSize: Int,
                         subgraphSizeInDwords: Int,
                         classDefinition: ClassDefinition,
                         fieldName: String?,
                         disposed: Boolean): Node {
      var localEdges = edges
      if (localEdges == null) {
        localEdges = HashMap(1)
        edges = localEdges
      }
      val node = localEdges.getOrPut(Edge(classDefinition, fieldName, disposed)) { RegularNode() }
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

  class RootNode(private val classStore: ClassStore) : Node {

    // In root node each instance has a separate path
    val edges = TIntObjectHashMap<Pair<RegularNode, Edge>>()

    override fun addEdge(objectId: Int,
                         objectSize: Int,
                         subgraphSizeInDwords: Int,
                         classDefinition: ClassDefinition,
                         fieldName: String?,
                         disposed: Boolean): Node {
      val nullableNode = edges.get(objectId)?.first
      val node: RegularNode

      if (nullableNode != null) {
        node = nullableNode
      }
      else {
        val newNode = RegularNode()
        val pair = Pair(newNode, Edge(classDefinition, fieldName, disposed))
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
      edges.forEachValue { (node, _) ->
        result += node.pathsCount
        true
      }
      return result
    }

    data class StackEntry(
      val edge: Edge,
      val node: RegularNode,
      val indent: String,
      val nextIndent: String
    )

    private class SoftWeakClassCache(private val classStore: ClassStore) {
      val softClasses = mutableSetOf<ClassDefinition>()
      val weakClasses = mutableSetOf<ClassDefinition>()
      val nonSoftWeakClasses = mutableSetOf<ClassDefinition>()

      fun getSoftWeakDescriptor(classDefinition: ClassDefinition): String? {
        if (softClasses.contains(classDefinition))
          return "soft"
        if (weakClasses.contains(classDefinition))
          return "weak"
        if (nonSoftWeakClasses.contains(classDefinition))
          return null

        var definition = classDefinition
        while (!classStore.isSoftOrWeakReferenceClass(definition)) {
          val superclassDefinition = definition.getSuperClass(classStore)
          if (superclassDefinition == null) {
            nonSoftWeakClasses.add(classDefinition)
            return null
          }
          definition = superclassDefinition
        }
        if (definition == classStore.weakReferenceClass) {
          weakClasses.add(classDefinition)
          return "weak"
        }
        else {
          assert(definition == classStore.softReferenceClass)
          softClasses.add(classDefinition)
          return "soft"
        }
      }

    }

    fun createHotPathReport(treeDisplayOptions: AnalysisConfig.TreeDisplayOptions, rootReasonGetter: (Int) -> String): String {
      val rootList = mutableListOf<Triple<Int, RegularNode, Edge>>()
      val result = StringBuilder()
      val printFunc = { s: String -> result.appendln(s); Unit }

      edges.forEachEntry { objectId, (node, edge) ->
        rootList.add(Triple(objectId, node, edge))
      }
      val totalInstanceCount = calculateTotalInstanceCount()

      val minimumObjectsForReport = Math.min(
        treeDisplayOptions.minimumObjectCount,
        (Math.ceil(totalInstanceCount / 100.0) * treeDisplayOptions.minimumObjectCountPercent).toInt())

      val softWeakClassCache = SoftWeakClassCache(classStore)

      // Show paths from roots that have at least minimumObjectCountPercent%, minimumObjectCount objects or size of all reported objects
      // in the subtree is more than minimumObjectSize.
      // Always show at least two paths.
      rootList
        .filterIndexed { index, (_, node, _) ->
          index < treeDisplayOptions.minimumPaths ||
          node.pathsCount >= minimumObjectsForReport ||
          node.pathsSize >= treeDisplayOptions.minimumObjectSize
        }
        .sortedByDescending { it.second.pathsSize }
        .forEachIndexed { index, (rootObjectId, rootNode, rootEdge) ->
          val rootReasonString = rootReasonGetter(rootObjectId)
          val rootPercent = (100.0 * rootNode.pathsCount / totalInstanceCount).toInt()

          result.appendln("Root ${index + 1}:")
          printReportLine(printFunc,
                          treeDisplayOptions,
                          rootNode.pathsCount,
                          rootPercent,
                          rootNode.pathsSize,
                          rootNode.totalSizeInDwords.toLong() * 4,
                          1,
                          false,
                          null,
                          false,
                          null,
                          "",
                          "ROOT: $rootReasonString")

          TruncatingPrintBuffer(treeDisplayOptions.headLimit, treeDisplayOptions.tailLimit, printFunc).use { buffer ->
            // Iterate over the hot path
            val stack = ArrayDeque<StackEntry>()
            stack.push(StackEntry(rootEdge, rootNode, "", ""))

            while (!stack.isEmpty()) {
              val (edge, node, indent, nextIndent) = stack.pop()
              val (classDefinition, fieldName, disposed) = edge

              // Soft/weak referents don't have a parent field set to differentiate them from other (strong-referencing) fields.
              val softWeakDescriptor = if (fieldName == null) softWeakClassCache.getSoftWeakDescriptor(classDefinition) else null

              printReportLine(buffer::println,
                              treeDisplayOptions,
                              node.pathsCount,
                              (100.0 * node.pathsCount / totalInstanceCount).toInt(),
                              node.pathsSize,
                              node.totalSizeInDwords.toLong() * 4,
                              node.instances.size(),
                              node.edges == null,
                              softWeakDescriptor,
                              disposed,
                              fieldName,
                              indent,
                              classDefinition.prettyName)

              val currentNodeEdges = node.edges ?: continue
              val childrenToReport =
                currentNodeEdges
                  .entries
                  .sortedByDescending { it.value.pathsSize }
                  .filterIndexed { index, e ->
                    index == 0 ||
                    e.value.pathsCount >= minimumObjectsForReport ||
                    e.value.pathsSize >= treeDisplayOptions.minimumObjectSize ||
                    e.value.totalSizeInDwords.toLong() * 4 >= treeDisplayOptions.minimumSubgraphSize
                  }
                  .asReversed()

              if (childrenToReport.size == 1 && treeDisplayOptions.smartIndent) {
                // No indentation for a single child
                stack.push(StackEntry(childrenToReport[0].key, childrenToReport[0].value, nextIndent, nextIndent))
              }
              else {
                // Don't report too deep paths
                if (nextIndent.length >= treeDisplayOptions.maximumIndent)
                  printReportLine(buffer::println,
                                  treeDisplayOptions,
                                  null, null, null, null,
                                  null, true, null, null, null,
                                  nextIndent, "\\-[...]")
                else {
                  // Add indentation only if there are 2+ children
                  childrenToReport.forEachIndexed { index, e ->
                    if (index == 0) stack.push(StackEntry(e.key, e.value, "$nextIndent\\-", "$nextIndent  "))
                    else stack.push(StackEntry(e.key, e.value, "$nextIndent+-", "$nextIndent| "))
                  }
                }
              }
            }
          }
        }
      return result.toString()
    }

    private fun printReportLine(printFunc: (String) -> Any,
                                treeDisplayOptions: AnalysisConfig.TreeDisplayOptions,
                                pathsCount: Int?,
                                percent: Int?,
                                instanceSize: Int?,
                                subgraphSize: Long?,
                                instanceCount: Int?,
                                lastInPath: Boolean,
                                softWeakDescriptor: String?,
                                disposed: Boolean?,
                                fieldName: String?,
                                indent: String,
                                text: String) {
      val pathsCountString = (pathsCount?.let { toShortStringAsCount(it.toLong()) } ?: "").padStart(STRING_PADDING_FOR_COUNT)
      val percentString = (percent?.let { "$it%" } ?: "").padStart(4)
      val instanceSizeString = (instanceSize?.let { toShortStringAsSize(it.toLong()) } ?: "").padStart(STRING_PADDING_FOR_SIZE)
      val instanceCountString = (instanceCount ?: "").toString().padStart(10)
      val status = if (lastInPath) "*" else if (softWeakDescriptor != null || disposed == true) "!" else " "
      val fieldNameString = if (fieldName != null) ".$fieldName" else ""
      val disposedString = if (disposed == true) " (disposed)" else ""
      val softWeakString = if (softWeakDescriptor != null) " ($softWeakDescriptor)" else ""
      val subgraphSizeString = (subgraphSize?.let { toShortStringAsSize(it) } ?: "").padStart(STRING_PADDING_FOR_SIZE)

      if (treeDisplayOptions.showSize) {
        printFunc(
          "[$pathsCountString/$percentString/$instanceSizeString] $subgraphSizeString $instanceCountString $status $indent$text$fieldNameString$disposedString$softWeakString")
      }
      else {
        printFunc("$status $indent$text$fieldNameString$disposedString$softWeakString")
      }
    }

    fun collectDisposedDominatorNodes(result: MutableMap<ClassDefinition, MutableList<RegularNode>>) {
      edges.forEachValue { (node, _) ->
        node.collectDisposedDominatorNodes(result)
        true
      }
    }
  }
}
