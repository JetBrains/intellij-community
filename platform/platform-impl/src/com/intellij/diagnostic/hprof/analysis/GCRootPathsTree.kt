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
import com.intellij.diagnostic.hprof.navigator.ObjectNavigator
import com.intellij.diagnostic.hprof.util.HeapReportUtils.Companion.STRING_PADDING_FOR_COUNT
import com.intellij.diagnostic.hprof.util.HeapReportUtils.Companion.STRING_PADDING_FOR_SIZE
import com.intellij.diagnostic.hprof.util.HeapReportUtils.Companion.toShortStringAsCount
import com.intellij.diagnostic.hprof.util.HeapReportUtils.Companion.toShortStringAsSize
import com.intellij.diagnostic.hprof.util.IntList
import com.intellij.diagnostic.hprof.util.TruncatingPrintBuffer
import gnu.trove.TIntArrayList
import gnu.trove.TIntHashSet
import gnu.trove.TIntObjectHashMap
import java.util.*

class GCRootPathsTree(
  private val disposedObjectsIDsSet: TIntHashSet,
  private val parentMapping: IntList,
  private val sizesMapping: IntList,
  private val nav: ObjectNavigator,
  private val allObjectsOfClass: ClassDefinition?
) {
  private val topNode = RootNode()
  private var countOfIgnoredObjects = 0

  // If all objects are of the same class and not arrays then instance size can be computed only once.
  private val cachedSize = allObjectsOfClass?.let { if (allObjectsOfClass.isArray()) null else it.instanceSize }

  fun registerObject(objectId: Int) {
    val gcPath = TIntArrayList()
    var objectIterationId = objectId
    var parentId = parentMapping[objectIterationId]
    var count = 0
    while (count < MAX_TREE_DEPTH && parentId != objectIterationId) {
      gcPath.add(objectIterationId)
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

    val size = if (cachedSize != null) cachedSize
    else {
      nav.goTo(objectId.toLong(), ObjectNavigator.ReferenceResolution.NO_REFERENCES)
      nav.getObjectSize()
    }

    var currentNode: Node = topNode
    for (i in gcPath.size() - 1 downTo 0) {
      val id = gcPath[i]
      val classDefinition = nav.getClassForObjectId(id.toLong())
      currentNode = currentNode.addEdge(id, size, sizesMapping[id], classDefinition, disposedObjectsIDsSet.contains(id))
    }
  }

  fun printTree(headLimit: Int, tailLimit: Int): String {
    val result = StringBuilder()
    if (countOfIgnoredObjects > 0) {
      result.append("Ignored ${countOfIgnoredObjects} too-deep objects\n")
    }
    val rootReasonGetter = { id: Int ->
      (nav.getRootReasonForObjectId(id.toLong())?.description ?: "<Couldn't find root description>")
    }
    result.append(topNode.createHotPathReport(rootReasonGetter, headLimit, tailLimit))
    return result.toString()
  }

  fun getDisposedDominatorNodes(): Map<ClassDefinition, List<RegularNode>> {
    val result = HashMap<ClassDefinition, MutableList<RegularNode>>()
    topNode.collectDisposedDominatorNodes(result)
    return result
  }

  interface Node {
    fun addEdge(objectId: Int, objectSize: Int, subgraphSizeInDwords: Int, classDefinition: ClassDefinition, disposed: Boolean): Node
  }

  data class Edge(val classDefinition: ClassDefinition, val disposed: Boolean)

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
                         disposed: Boolean): Node {
      var localEdges = edges
      if (localEdges == null) {
        localEdges = HashMap(1)
        edges = localEdges
      }
      val node = localEdges.getOrPut(Edge(classDefinition, disposed)) { RegularNode() }
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

  class RootNode : Node {

    // In root node each instance has a separate path
    val edges = TIntObjectHashMap<Pair<RegularNode, ClassDefinition>>()

    override fun addEdge(objectId: Int,
                         objectSize: Int,
                         subgraphSizeInDwords: Int,
                         classDefinition: ClassDefinition,
                         disposed: Boolean): Node {
      val nullableNode = edges.get(objectId)?.first
      val node: RegularNode

      if (nullableNode != null) {
        node = nullableNode
      }
      else {
        val newNode = RegularNode()
        val pair = Pair(newNode, classDefinition)
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

    fun createHotPathReport(rootReasonGetter: (Int) -> String, headLimit: Int, tailLimit: Int): String {
      val rootList = mutableListOf<Triple<Int, RegularNode, ClassDefinition>>()
      val result = StringBuilder()
      edges.forEachEntry { objectId, (node, classDef) ->
        rootList.add(Triple(objectId, node, classDef))
      }
      rootList.sortByDescending { it.second.pathsSize }
      val totalInstanceCount = calculateTotalInstanceCount()

      val minimumObjectsForReport = Math.min(
        MINIMUM_OBJECT_COUNT_FOR_REPORT,
        (Math.ceil(totalInstanceCount / 100.0) * MINIMUM_OBJECT_COUNT_PERCENT).toInt())

      // Show paths from roots that have at least MINIMUM_OBJECT_COUNT_PERCENT or MINIMUM_OBJECT_COUNT_FOR_REPORT objects.
      // Always show at least two paths.
      rootList.filterIndexed { index, (_, node, _) ->
        index <= 1 || node.pathsCount >= minimumObjectsForReport || node.pathsSize >= MINIMUM_OBJECT_SIZE_FOR_REPORT
      }.forEach { (rootObjectId, rootNode, rootObjectClass) ->
        val printFunc = { s: String -> result.appendln(s); Unit }

        val rootReasonString = rootReasonGetter(rootObjectId)

        val rootPercent = (100.0 * rootNode.pathsCount / totalInstanceCount).toInt()

        result.appendln("ROOT: $rootReasonString: ${rootNode.pathsCount} objects ($rootPercent%), ${toShortStringAsSize(
          rootNode.pathsSize.toLong())}")

        TruncatingPrintBuffer(headLimit, tailLimit, printFunc).use { buffer ->
          // Iterate over the hot path
          val stack = ArrayDeque<StackEntry>()
          stack.push(StackEntry(Edge(rootObjectClass, false), rootNode, "", ""))

          while (!stack.isEmpty()) {
            val (edge, node, indent, nextIndent) = stack.pop()
            val (classDefinition, disposed) = edge

            printReportLine(buffer::println,
                            node.pathsCount,
                            (100.0 * node.pathsCount / totalInstanceCount).toInt(),
                            node.instances.size(),
                            node.pathsSize,
                            node.totalSizeInDwords.toLong() * 4,
                            node.edges == null,
                            disposed,
                            indent,
                            classDefinition.prettyName)

            val currentNodeEdges = node.edges ?: continue
            val childrenToReport =
              currentNodeEdges
                .entries
                .sortedByDescending { it.value.pathsSize }
                .filterIndexed { index, e ->
                  index == 0 || e.value.pathsCount >= minimumObjectsForReport || e.value.pathsSize >= MINIMUM_OBJECT_SIZE_FOR_REPORT
                }
                .asReversed()

            if (childrenToReport.size == 1) {
              // No indentation for a single child
              stack.push(StackEntry(childrenToReport[0].key, childrenToReport[0].value, nextIndent, nextIndent))
            }
            else {
              // Don't report too deep paths
              if (nextIndent.length >= MAX_INDENT)
                printReportLine(buffer::println,
                                null, null, null, null,
                                null, true, null,
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
                                pathsCount: Int?,
                                percent: Int?,
                                instanceCount: Int?,
                                instanceSize: Int?,
                                subgraphSize: Long?,
                                lastInPath: Boolean,
                                disposed: Boolean?,
                                indent: String,
                                text: String) {
      val pathsCountString = (pathsCount?.let { toShortStringAsCount(it.toLong()) } ?: "").padStart(STRING_PADDING_FOR_COUNT)
      val percentString = (percent?.let { "$it%" } ?: "").padStart(4)
      val instanceCountString = (instanceCount ?: "").toString().padStart(10)
      val lastInPathString = if (lastInPath) "*" else " "
      val disposedString = if (disposed == true) " (disposed)" else ""
      val instanceSizeString = (instanceSize?.let { toShortStringAsSize(it.toLong()) } ?: "").padStart(STRING_PADDING_FOR_SIZE)
      val subgraphSizeString = (subgraphSize?.let { toShortStringAsSize(it) } ?: "").padStart(STRING_PADDING_FOR_SIZE)

      printFunc(
        "[$pathsCountString/$percentString/$instanceSizeString] $subgraphSizeString $instanceCountString $lastInPathString $indent$text$disposedString")
    }

    fun collectDisposedDominatorNodes(result: MutableMap<ClassDefinition, MutableList<RegularNode>>) {
      edges.forEachValue { (node, _) ->
        node.collectDisposedDominatorNodes(result)
        true
      }
    }
  }

  companion object {
    private const val MINIMUM_OBJECT_SIZE_FOR_REPORT = 10_000_000
    private const val MINIMUM_OBJECT_COUNT_FOR_REPORT = 10_000
    private const val MINIMUM_OBJECT_COUNT_PERCENT = 10
    private const val MAX_TREE_DEPTH = 500
    private const val MAX_INDENT = 40
  }
}
