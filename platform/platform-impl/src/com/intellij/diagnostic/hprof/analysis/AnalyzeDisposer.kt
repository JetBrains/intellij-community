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
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toPaddedShortStringAsSize
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toShortStringAsCount
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toShortStringAsSize
import com.intellij.diagnostic.hprof.util.TreeNode
import com.intellij.diagnostic.hprof.util.TreeVisualizer
import com.intellij.diagnostic.hprof.util.TruncatingPrintBuffer
import com.intellij.util.ExceptionUtil
import it.unimi.dsi.fastutil.longs.*
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.*
import java.util.function.LongConsumer

class AnalyzeDisposer(private val analysisContext: AnalysisContext) {

  private var prepareException: Exception? = null

  data class Grouping(val childClass: ClassDefinition,
                      val parentClass: ClassDefinition?,
                      val rootClass: ClassDefinition)

  class InstanceStats {
    private val parentIds = LongArrayList()
    private val rootIds = LongOpenHashSet()

    fun parentCount() = LongOpenHashSet(parentIds).size
    fun rootCount() = rootIds.size
    fun objectCount() = parentIds.size

    fun registerObject(parentId: Long, rootId: Long) {
      parentIds.add(parentId)
      rootIds.add(rootId)
    }
  }

  data class DisposedDominatorReportEntry(val classDefinition: ClassDefinition, val count: Long, val size: Long)

  companion object {
    val TOP_REPORTED_CLASSES = setOf(
      "com.intellij.openapi.project.impl.ProjectImpl"
    )
  }

  fun prepareDisposerChildren() {
    prepareException = null
    val result = analysisContext.disposerParentToChildren
    result.clear()

    if (!analysisContext.classStore.containsClass("com.intellij.openapi.util.Disposer")) {
      return
    }
    try {
      val nav = analysisContext.navigator

      nav.goToStaticField("com.intellij.openapi.util.Disposer", "ourTree")
      analysisContext.disposerTreeObjectId = nav.id.toInt()

      if (nav.getClass().refInstanceFields.any { it.name == "myObject2NodeMap" }) {
        goToArrayOfDisposableObjectNodes(nav)
        collectDisposerParentToChildren(nav, result)
      }
      else {
        collectDisposerParentToChildrenNew(nav, result)
      }
      result.values.forEach(LongArrayList::trim)
    }
    catch (ex: Exception) {
      prepareException = ex
    }
  }

  private fun collectDisposerParentToChildren(nav: ObjectNavigator, result: Long2ObjectOpenHashMap<LongArrayList>) {
    nav.getReferencesCopy().forEach {
      if (it == 0L) return@forEach

      nav.goTo(it)
      verifyClassIsObjectNode(nav.getClass())
      val objectNodeParentId = nav.getInstanceFieldObjectId(null, "myParent")
      val childId = nav.getInstanceFieldObjectId(null, "myObject")
      nav.goTo(objectNodeParentId)

      val parentId = if (nav.isNull()) {
        0L
      }
      else {
        nav.getInstanceFieldObjectId(null, "myObject")
      }

      val childrenList = result.getOrPut(parentId) { LongArrayList() }
      childrenList.add(childId)
    }
  }

  private fun goToArrayOfDisposableObjectNodes(nav: ObjectNavigator) {
    nav.goToStaticField("com.intellij.openapi.util.Disposer", "ourTree")

    analysisContext.disposerTreeObjectId = nav.id.toInt()

    verifyClassIsObjectTree(nav.getClass())

    if (nav.isNull()) {
      throw ObjectNavigator.NavigationException("Disposer.ourTree == null")
    }
    nav.goToInstanceField(null, "myObject2NodeMap")
    if (nav.getClass().name == "gnu.trove.THashMap") {
      nav.goToInstanceField("gnu.trove.THashMap", "_values")
    }
    else {
      nav.goToInstanceField("it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap", "value")
    }
    if (nav.isNull()) {
      throw ObjectNavigator.NavigationException("Collection of children is null")
    }
    if (!nav.getClass().isArray()) {
      throw ObjectNavigator.NavigationException("Invalid type of map values collection: ${nav.getClass().name}")
    }
  }

  private fun collectDisposerParentToChildrenNew(nav: ObjectNavigator, result: Long2ObjectOpenHashMap<LongArrayList>) {
    nav.goToStaticField("com.intellij.openapi.util.Disposer", "ourTree")
    analysisContext.disposerTreeObjectId = nav.id.toInt()
    nav.goToInstanceField("com.intellij.openapi.util.ObjectTree", "myRootNode")
    val rootObjectNodeIds = getObjectNodeChildrenIds(nav)
    for (i in 0 until rootObjectNodeIds.size) {
      val rootObjectNodeId = rootObjectNodeIds.getLong(i)
      nav.goTo(rootObjectNodeId)
      if (nav.isNull()) continue
      val rootObjectId = nav.getInstanceFieldObjectId("com.intellij.openapi.util.ObjectNode", "myObject")
      nav.goTo(rootObjectNodeId)
      visitObjectTreeRecursively(nav, rootObjectNodeId, 0L, result/*, null, rootObjectId, rootObjectClass,
                                 tooDeepObjectClasses, maxTreeDepth, 0, groupingToObjectStats*/)
    }
  }

  private fun visitObjectTreeRecursively(nav: ObjectNavigator,
                                         currentNodeId: Long,
                                         parentObjectId: Long,
                                         result: Long2ObjectOpenHashMap<LongArrayList>) {
    nav.goTo(currentNodeId)
    if (nav.isNull()) return
    val currentObjectId = nav.getInstanceFieldObjectId("com.intellij.openapi.util.ObjectNode", "myObject")
    result.getOrPut(parentObjectId) { LongArrayList() }.add(currentObjectId)
    val childrenNodeIds = getObjectNodeChildrenIds(nav)
    for (i in 0 until childrenNodeIds.size) {
      val childNodeId = childrenNodeIds.getLong(i)
      visitObjectTreeRecursively(nav, childNodeId, currentObjectId, result)
    }
  }

  private fun getObjectNodeChildrenIds(nav: ObjectNavigator): LongList {
    nav.goToInstanceField("com.intellij.openapi.util.ObjectNode", "myChildren")
    return when (nav.getClass().name) {
      Collections.emptyList<Any>().javaClass.name, "com.intellij.openapi.util.ObjectNode\$1" -> {
        // no children
        LongList.of()
      }
      "com.intellij.openapi.util.ObjectNode\$ListNodeChildren" -> {
        nav.goToInstanceField("com.intellij.openapi.util.ObjectNode\$ListNodeChildren", "myChildren")
        getSmartListChildren(nav)
      }
      "com.intellij.openapi.util.ObjectNode\$MapNodeChildren" -> {
        nav.goToInstanceField("com.intellij.openapi.util.ObjectNode\$MapNodeChildren", "myChildren")
        getMapNodeChildren(nav)
      }
      else -> {
        getSmartListChildren(nav)
      }
    }
  }

  private fun getSmartListChildren(nav: ObjectNavigator): LongList {
    nav.goToInstanceField("com.intellij.util.SmartList", "myElem")
    if (nav.isNull()) return LongList.of()
    if (nav.getClass().isArray()) {
      return nav.getReferencesCopy()
    }
    else {
      // myElem is ObjectNode
      return LongList.of(nav.id)
    }
  }

  private fun getMapNodeChildren(nav: ObjectNavigator): LongList {
    nav.goToInstanceField("it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap", "value")
    return nav.getReferencesCopy()
  }

  private fun verifyClassIsObjectNode(clazzObjectTree: ClassDefinition) {
    if (clazzObjectTree.undecoratedName != "com.intellij.openapi.util.objectTree.ObjectNode" &&
        clazzObjectTree.undecoratedName != "com.intellij.openapi.util.ObjectNode") {
      throw ObjectNavigator.NavigationException("Wrong type, expected ObjectNode: ${clazzObjectTree.name}")
    }
  }

  private fun verifyClassIsObjectTree(clazzObjectTree: ClassDefinition) {
    if (clazzObjectTree.undecoratedName != "com.intellij.openapi.util.objectTree.ObjectTree" &&
        clazzObjectTree.undecoratedName != "com.intellij.openapi.util.ObjectTree") {
      throw ObjectNavigator.NavigationException("Wrong type, expected ObjectTree: ${clazzObjectTree.name}")
    }
  }

  private class DisposerNode(val className: String) : TreeNode {
    var count = 0
    var subtreeSize = 0
    var filteredSubtreeSize = 0
    val children = HashMap<String, DisposerNode>()

    fun equals(other: DisposerNode): Boolean = className == other.className

    override fun equals(other: Any?): Boolean = other != null && other is DisposerNode && equals(other)
    override fun hashCode() = className.hashCode()
    override fun description(): String = "[$subtreeSize] $count $className"
    override fun children(): Collection<TreeNode> = children.values.sortedByDescending { it.subtreeSize }

    fun addInstance() {
      count++
    }

    fun getChildForClassName(name: String): DisposerNode = children.getOrPut(name, { DisposerNode(name) })
  }

  private enum class SubTreeUpdaterOperation  { PROCESS_CHILDREN, UPDATE_SIZE }

  fun prepareDisposerTreeSummarySection(
    disposerParentToChildren: Long2ObjectOpenHashMap<LongArrayList>,
    options: AnalysisConfig.DisposerTreeSummaryOptions
  ): String = buildString {
    TruncatingPrintBuffer(options.headLimit, 0, this::appendLine).use { buffer ->
      if (!analysisContext.classStore.containsClass("com.intellij.openapi.util.Disposer")) {
        return@buildString
      }

      prepareException?.let {
        buffer.println(ExceptionUtil.getThrowableText(it))
        return@buildString
      }

      val nav = analysisContext.navigator

      try {
        val rootNode = buildDisposerTree(analysisContext.disposerParentToChildren, nav)

        // Update subtree size
        data class SubtreeSizeUpdateStackObject(val node: DisposerNode, val operation: SubTreeUpdaterOperation)

        val nodeStack = Stack<SubtreeSizeUpdateStackObject>()
        nodeStack.push(SubtreeSizeUpdateStackObject(rootNode, SubTreeUpdaterOperation.PROCESS_CHILDREN))

        while (!nodeStack.isEmpty()) {
          val (currentNode, operation) = nodeStack.pop()
          if (operation == SubTreeUpdaterOperation.PROCESS_CHILDREN) {
            currentNode.subtreeSize = currentNode.count
            currentNode.filteredSubtreeSize = currentNode.count
            nodeStack.push(SubtreeSizeUpdateStackObject(currentNode, SubTreeUpdaterOperation.UPDATE_SIZE))
            currentNode.children.values.forEach {
              nodeStack.push(SubtreeSizeUpdateStackObject(it, SubTreeUpdaterOperation.PROCESS_CHILDREN))
            }
          }
          else {
            assert(operation == SubTreeUpdaterOperation.UPDATE_SIZE)

            currentNode.children.values.forEach { currentNode.subtreeSize += it.subtreeSize }
            currentNode.children.entries.removeIf { it.value.filteredSubtreeSize < options.nodeCutoff }
            currentNode.children.values.forEach { currentNode.filteredSubtreeSize += it.subtreeSize }
          }
        }
        val visualizer = TreeVisualizer()
        buffer.println("Cutoff: ${options.nodeCutoff}")
        buffer.println("Count of disposable objects: ${rootNode.subtreeSize}")
        buffer.println()
        rootNode.children().forEach {
          visualizer.visualizeTree(it, buffer, analysisContext.config.disposerOptions.disposerTreeSummaryOptions)
          buffer.println()
        }
      }
      catch (ex: Exception) {
        buffer.println(ExceptionUtil.getThrowableText(ex))
      }
    }
  }

  /**
   * Build a map: object -> list of its disposable children
   * Collect top-level objects (i.e. have no parent)
   */
  private fun buildDisposerTree(disposerParentToChildren: Long2ObjectOpenHashMap<LongArrayList>,
                                nav: ObjectNavigator): DisposerNode {
    val rootNode = DisposerNode("<root>")

    data class StackObject(val node: DisposerNode, val childrenIds: LongCollection)

    val stack = Stack<StackObject>()
    stack.push(StackObject(rootNode, disposerParentToChildren[0L]))

    while (!stack.empty()) {
      val (currentNode, childrenIds) = stack.pop()

      val nodeToChildren = HashMap<DisposerNode, LongArrayList>()
      childrenIds.forEach(LongConsumer {
        val childClassName = nav.getClassForObjectId(it).name
        val childNode = currentNode.getChildForClassName(childClassName)
        childNode.addInstance()
        disposerParentToChildren[it]?.let {
          nodeToChildren.getOrPut(childNode) { LongArrayList() }.addAll(it)
        }
      })
      nodeToChildren.forEach { (node, children) -> stack.push(StackObject(node, children)) }
    }
    return rootNode
  }

  fun prepareDisposerTreeSection(): String = buildString {
    if (!analysisContext.classStore.containsClass("com.intellij.openapi.util.Disposer")) {
      return@buildString
    }

    prepareException?.let {
      appendLine(ExceptionUtil.getThrowableText(it))
      return@buildString
    }

    val nav = analysisContext.navigator

    try {
      val groupingToObjectStats = HashMap<Grouping, InstanceStats>()
      val tooDeepObjectClasses = HashSet<ClassDefinition>()

      val parentMap = Long2LongOpenHashMap()
      analysisContext.disposerParentToChildren.forEach { parentId, childrenId ->
        childrenId.forEach { childId -> parentMap.put(childId, parentId) }
      }

      val maxTreeDepth = 200
      analysisContext.disposerParentToChildren.forEach { parentId, childrenId ->
        childrenId.forEach { childId ->
          val parentClass =
            if (parentId == 0L)
              null
            else {
              nav.goTo(parentId)
              nav.getClass()
            }

          nav.goTo(childId)
          val objectClass = nav.getClass()

          val rootClass: ClassDefinition
          val rootId: Long

          if (parentId == 0L) {
            rootClass = objectClass
            rootId = childId
          }
          else {
            var rootObjectId: Long = parentId
            var iterationCount = 0
            while (parentMap[rootObjectId] != 0L) {
              rootObjectId = parentMap[rootObjectId]
              iterationCount++
              if (iterationCount == maxTreeDepth) break
            }

            if (iterationCount >= maxTreeDepth) {
              tooDeepObjectClasses.add(objectClass)
              rootId = parentId
              rootClass = parentClass!!
            }
            else {
              nav.goTo(rootObjectId)
              rootId = rootObjectId
              rootClass = nav.getClass()
            }
          }

          groupingToObjectStats
            .getOrPut(Grouping(objectClass, parentClass, rootClass)) { InstanceStats() }
            .registerObject(parentId, rootId)
        }
      }

      TruncatingPrintBuffer(400, 0, this::appendLine).use { buffer ->
        groupingToObjectStats
          .entries
          .sortedByDescending { it.value.objectCount() }
          .groupBy { it.key.rootClass }
          .forEach { (rootClass, entries) ->
            buffer.println("Root: ${rootClass.name}")
            TruncatingPrintBuffer(100, 0, buffer::println).use { buffer ->
              entries.forEach { (mapping, groupedObjects) ->
                printDisposerTreeReportLine(buffer, mapping, groupedObjects)
              }
            }
            buffer.println()
          }
      }

      if (tooDeepObjectClasses.size > 0) {
        appendLine("Skipped analysis of objects too deep in disposer tree:")
        tooDeepObjectClasses.forEach {
          appendLine(" * ${nav.classStore.getShortPrettyNameForClass(it)}")
        }
      }
    } catch (ex : Exception) {
      appendLine(ExceptionUtil.getThrowableText(ex))
    }
  }

  private fun printDisposerTreeReportLine(buffer: TruncatingPrintBuffer,
                                          mapping: Grouping,
                                          groupedObjects: InstanceStats) {
    val (sourceClass, parentClass, rootClass) = mapping
    val nav = analysisContext.navigator

    val objectCount = groupedObjects.objectCount()
    val parentCount = groupedObjects.parentCount()

    // Ignore 1-1 mappings
    if (parentClass != null && objectCount == parentCount)
      return

    val parentString: String
    if (parentClass == null) {
      parentString = "(no parent)"
    }
    else {
      val parentClassName = nav.classStore.getShortPrettyNameForClass(parentClass)
      val rootCount = groupedObjects.rootCount()
      if (rootClass != parentClass || rootCount != parentCount) {
        parentString = "<-- $parentCount $parentClassName [...] $rootCount"
      }
      else
        parentString = "<-- $parentCount"
    }

    val sourceClassName = nav.classStore.getShortPrettyNameForClass(sourceClass)
    buffer.println("  ${String.format("%6d", objectCount)} $sourceClassName $parentString")
  }

  fun computeDisposedObjectsIDs() {
    val disposedObjectsIDs = analysisContext.disposedObjectsIDs
    disposedObjectsIDs.clear()

    if (prepareException != null) {
      return
    }

    try {
      val nav = analysisContext.navigator
      val parentList = analysisContext.parentList

      if (!nav.classStore.containsClass("com.intellij.openapi.util.Disposer")) {
        return
      }

      nav.goToStaticField("com.intellij.openapi.util.Disposer", "ourTree")
      if (nav.isNull()) {
        throw ObjectNavigator.NavigationException("ourTree is null")
      }
      verifyClassIsObjectTree(nav.getClass())
      nav.goToInstanceField(null, "myDisposedObjects")
      nav.goToInstanceField("com.intellij.util.containers.WeakHashMap", "myMap")
      nav.goToInstanceField("com.intellij.util.containers.RefHashMap\$MyMap", "key")
      val weakKeyClass = nav.classStore.getClassIfExists("com.intellij.util.containers.WeakHashMap\$WeakKey")

      nav.getReferencesCopy().forEach {
        if (it == 0L) {
          return@forEach
        }
        nav.goTo(it, ObjectNavigator.ReferenceResolution.ALL_REFERENCES)
        if (nav.getClass() != weakKeyClass) {
          return@forEach
        }
        nav.goToInstanceField("com.intellij.util.containers.WeakHashMap\$WeakKey", "referent")
        if (nav.id == 0L) return@forEach

        val leakId = nav.id.toInt()
        if (parentList.get(leakId) == 0) {
          // If there is no parent, then the object does not have a strong-reference path to GC root
          return@forEach
        }
        disposedObjectsIDs.add(leakId)
      }
    }
    catch (navEx: ObjectNavigator.NavigationException) {
      prepareException = navEx
    }
  }

  fun prepareDisposedObjectsSection(): String = buildString {
    val leakedInstancesByClass = HashMap<ClassDefinition, LongList>()
    val countByClass = Object2IntOpenHashMap<ClassDefinition>()
    var totalCount = 0

    val nav = analysisContext.navigator
    val disposedObjectsIDs = analysisContext.disposedObjectsIDs
    val disposerOptions = analysisContext.config.disposerOptions

    disposedObjectsIDs.forEach {
      nav.goTo(it.toLong(), ObjectNavigator.ReferenceResolution.ALL_REFERENCES)

      val leakClass = nav.getClass()
      val leakId = nav.id

      leakedInstancesByClass.computeIfAbsent(leakClass) { LongArrayList() }.add(leakId)

      countByClass.put(leakClass, countByClass.getInt(leakClass) + 1)
      totalCount++
    }

    // Convert TObjectIntHashMap to list of entries
    data class TObjectIntMapEntry<T>(val key: T, val value: Int)

    val entries = mutableListOf<TObjectIntMapEntry<ClassDefinition>>()
    countByClass.object2IntEntrySet().fastForEach {
      entries.add(TObjectIntMapEntry(it.key, it.intValue))
    }

    if (disposerOptions.includeDisposedObjectsSummary) {
      // Print counts of disposed-but-strong-referenced objects
      TruncatingPrintBuffer(100, 0, this::appendLine).use { buffer ->
        buffer.println("Count of disposed-but-strong-referenced objects: $totalCount")
        entries
          .sortedByDescending { it.value }
          .partition { TOP_REPORTED_CLASSES.contains(it.key.name) }
          .let { it.first + it.second }
          .forEach { entry ->
            buffer.println("  ${entry.value} ${entry.key.prettyName}")
          }
      }
      appendLine()
    }

    val disposedTree = GCRootPathsTree(analysisContext, AnalysisConfig.TreeDisplayOptions.all(), null)
    for (disposedObjectsID in disposedObjectsIDs) {
      disposedTree.registerObject(disposedObjectsID)
    }

    val disposedDominatorNodesByClass = disposedTree.getDisposedDominatorNodes()
    var allDominatorsCount = 0L
    var allDominatorsSubgraphSize = 0L
    val disposedDominatorClassSizeList = mutableListOf<DisposedDominatorReportEntry>()
    disposedDominatorNodesByClass.forEach { (classDefinition, nodeList) ->
      var dominatorClassSubgraphSize = 0L
      var dominatorClassInstanceCount = 0L
      nodeList.forEach {
        dominatorClassInstanceCount += it.instances.size
        dominatorClassSubgraphSize += it.totalSizeInDwords.toLong() * 4
      }
      allDominatorsCount += dominatorClassInstanceCount
      allDominatorsSubgraphSize += dominatorClassSubgraphSize
      disposedDominatorClassSizeList.add(
        DisposedDominatorReportEntry(classDefinition, dominatorClassInstanceCount, dominatorClassSubgraphSize))
    }

    if (disposerOptions.includeDisposedObjectsSummary) {
      TruncatingPrintBuffer(30, 0, this::appendLine).use { buffer ->
        buffer.println("Disposed-but-strong-referenced dominator object count: $allDominatorsCount")
        buffer.println(
          "Disposed-but-strong-referenced dominator sub-graph size: ${toShortStringAsSize(allDominatorsSubgraphSize)}")
        disposedDominatorClassSizeList
          .sortedByDescending { it.size }
          .forEach { entry ->
            buffer.println(
              "  ${toPaddedShortStringAsSize(entry.size)} - ${toShortStringAsCount(entry.count)} ${entry.classDefinition.name}")
          }
      }
      appendLine()
    }

    if (disposerOptions.includeDisposedObjectsDetails) {
      val instancesListInOrder = getInstancesListInPriorityOrder(
        leakedInstancesByClass,
        disposedDominatorClassSizeList
      )

      TruncatingPrintBuffer(700, 0, this::appendLine).use { buffer ->
        instancesListInOrder
          .forEach { instances ->
            nav.goTo(instances.getLong(0))
            buffer.println(
              "Disposed but still strong-referenced objects: ${instances.size} ${nav.getClass().prettyName}, most common paths from GC-roots:")
            val gcRootPathsTree = GCRootPathsTree(analysisContext, disposerOptions.disposedObjectsDetailsTreeDisplayOptions, nav.getClass())
            instances.forEach { leakId ->
              gcRootPathsTree.registerObject(leakId.toInt())
            }
            gcRootPathsTree.printTree().lineSequence().forEach(buffer::println)
          }
      }
    }
  }

  private fun getInstancesListInPriorityOrder(
    classToLeakedIdsList: HashMap<ClassDefinition, LongList>,
    disposedDominatorReportEntries: List<DisposedDominatorReportEntry>): List<LongList> {
    val result = mutableListOf<LongList>()

    // Make a mutable copy. When a class instances are added to the result list, remove the class entry from the copy.
    val classToLeakedIdsListCopy = HashMap(classToLeakedIdsList)

    // First, all top classes
    TOP_REPORTED_CLASSES.forEach { topClassName ->
      classToLeakedIdsListCopy
        .filterKeys { it.name == topClassName }
        .forEach { (classDefinition, list) ->
          result.add(list)
          classToLeakedIdsListCopy.remove(classDefinition)
        }
    }

    // Alternate between class with most instances leaked and class with most bytes leaked

    // Prepare instance count class list by priority
    val classOrderByInstanceCount = ArrayDeque(
      classToLeakedIdsListCopy
        .entries
        .sortedByDescending { it.value.size }
        .map { it.key }
    )

    // Prepare dominator bytes count class list by priority
    val classOrderByByteCount = ArrayDeque(
      disposedDominatorReportEntries
        .sortedByDescending { it.size }
        .map { it.classDefinition }
    )

    // zip, but ignore duplicates
    var nextByInstanceCount = true
    while (!classOrderByInstanceCount.isEmpty() ||
           !classOrderByByteCount.isEmpty()) {
      val nextCollection = if (nextByInstanceCount) classOrderByInstanceCount else classOrderByByteCount
      if (!nextCollection.isEmpty()) {
        val nextClass = nextCollection.removeFirst()
        val list = classToLeakedIdsListCopy.remove(nextClass) ?: continue
        result.add(list)
      }
      nextByInstanceCount = !nextByInstanceCount
    }
    return result
  }

}