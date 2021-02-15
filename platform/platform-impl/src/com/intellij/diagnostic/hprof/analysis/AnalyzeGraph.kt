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

import com.google.common.base.Stopwatch
import com.intellij.diagnostic.DiagnosticBundle
import com.intellij.diagnostic.hprof.classstore.ClassDefinition
import com.intellij.diagnostic.hprof.histogram.Histogram
import com.intellij.diagnostic.hprof.navigator.ObjectNavigator
import com.intellij.diagnostic.hprof.util.HeapReportUtils.sectionHeader
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toPaddedShortStringAsCount
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toPaddedShortStringAsSize
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toShortStringAsCount
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toShortStringAsSize
import com.intellij.diagnostic.hprof.util.PartialProgressIndicator
import com.intellij.diagnostic.hprof.visitors.HistogramVisitor
import com.intellij.openapi.progress.ProgressIndicator
import it.unimi.dsi.fastutil.ints.*
import it.unimi.dsi.fastutil.longs.LongArrayList

fun analyzeGraph(analysisContext: AnalysisContext, progress: ProgressIndicator): String {
  return AnalyzeGraph(analysisContext).analyze(progress)
}

open class AnalyzeGraph(protected val analysisContext: AnalysisContext) {

  private var strongRefHistogram: Histogram? = null
  private var softWeakRefHistogram: Histogram? = null

  private val parentList = analysisContext.parentList

  private fun setParentForObjectId(objectId: Long, parentId: Long) {
    parentList[objectId.toInt()] = parentId.toInt()
  }

  private fun getParentIdForObjectId(objectId: Long): Long {
    return parentList[objectId.toInt()].toLong()
  }

  private val nominatedInstances = HashMap<ClassDefinition, IntSet>()

  open fun analyze(progress: ProgressIndicator): String {
    val sb = StringBuilder()

    val includePerClassSection = analysisContext.config.perClassOptions.classNames.isNotEmpty()

    val traverseProgress =
      if (includePerClassSection) PartialProgressIndicator(progress, 0.0, 0.5) else progress

    traverseInstanceGraph(traverseProgress)

    val analyzeDisposer = AnalyzeDisposer(analysisContext)
    analyzeDisposer.computeDisposedObjectsIDs()

    // Histogram section
    val histogramOptions = analysisContext.config.histogramOptions
    if (histogramOptions.includeByCount || histogramOptions.includeBySize) {
      sb.appendLine(sectionHeader("Histogram"))
      sb.append(prepareHistogramSection())
    }

    // Per-class section
    if (includePerClassSection) {
      val perClassProgress = PartialProgressIndicator(progress, 0.5, 0.5)
      sb.appendLine(sectionHeader("Instances of each nominated class"))
      sb.append(preparePerClassSection(perClassProgress))
    }

    // Disposer sections
    if (config.disposerOptions.includeDisposerTree) {
      sb.appendLine(sectionHeader("Disposer tree"))
      sb.append(analyzeDisposer.prepareDisposerTreeSection())
    }
    if (config.disposerOptions.includeDisposedObjectsSummary || config.disposerOptions.includeDisposedObjectsDetails) {
      sb.appendLine(sectionHeader("Disposed objects"))
      sb.append(analyzeDisposer.prepareDisposedObjectsSection())
    }

    return sb.toString()
  }

  private fun preparePerClassSection(progress: PartialProgressIndicator): String {
    val sb = StringBuilder()
    val histogram = analysisContext.histogram
    val perClassOptions = analysisContext.config.perClassOptions

    if (perClassOptions.includeClassList) {
      sb.appendLine("Nominated classes:")
      perClassOptions.classNames.forEach { name ->
        val (classDefinition, totalInstances, totalBytes) =
          histogram.entries.find { entry -> entry.classDefinition.name == name } ?: return@forEach
        val prettyName = classDefinition.prettyName
        sb.appendLine(" --> [${toShortStringAsCount(totalInstances)}/${toShortStringAsSize(totalBytes)}] " + prettyName)
      }
      sb.appendLine()
    }

    val nav = analysisContext.navigator
    var counter = 0
    val nominatedClassNames = config.perClassOptions.classNames
    val stopwatch = Stopwatch.createUnstarted()
    nominatedClassNames.forEach { className ->
      val classDefinition = nav.classStore[className]
      val set = nominatedInstances[classDefinition]!!
      progress.fraction = counter.toDouble() / nominatedInstances.size
      progress.text2 = DiagnosticBundle.message("hprof.analysis.progress", set.size, classDefinition.prettyName)
      stopwatch.reset().start()
      sb.appendLine("CLASS: ${classDefinition.prettyName} (${set.size} objects)")
      val referenceRegistry = GCRootPathsTree(analysisContext, perClassOptions.treeDisplayOptions, classDefinition)
      set.forEach { objectId ->
        referenceRegistry.registerObject(objectId)
      }
      set.clear()
      sb.append(referenceRegistry.printTree())
      if (config.metaInfoOptions.include) {
        sb.appendLine("Report for ${classDefinition.prettyName} created in $stopwatch")
      }
      sb.appendLine()
      counter++
    }
    progress.fraction = 1.0

    return sb.toString()
  }

  private fun prepareHistogramSection(): String {
    val result = StringBuilder()
    val strongRefHistogram = getAndClearStrongRefHistogram()
    val softWeakRefHistogram = getAndClearSoftWeakHistogram()

    val histogram = analysisContext.histogram
    val histogramOptions = analysisContext.config.histogramOptions

    result.append(
      Histogram.prepareMergedHistogramReport(histogram, "All",
                                             strongRefHistogram, "Strong-ref", histogramOptions))

    val unreachableObjectsCount = histogram.instanceCount - strongRefHistogram.instanceCount - softWeakRefHistogram.instanceCount
    val unreachableObjectsSize = histogram.bytesCount - strongRefHistogram.bytesCount - softWeakRefHistogram.bytesCount
    result.appendLine("Unreachable objects: ${toPaddedShortStringAsCount(unreachableObjectsCount)}  ${toPaddedShortStringAsSize(unreachableObjectsSize)}")

    return result.toString()
  }

  enum class WalkGraphPhase {
    StrongReferencesNonLocalVariables,
    StrongReferencesLocalVariables,
    SoftReferences,
    WeakReferences,
    CleanerFinalizerReferences,
    Finished
  }

  private val config = analysisContext.config

  protected fun traverseInstanceGraph(progress: ProgressIndicator): String {
    val result = StringBuilder()

    val nav = analysisContext.navigator
    val classStore = analysisContext.classStore
    val sizesList = analysisContext.sizesList
    val visitedList = analysisContext.visitedList
    val refIndexList = analysisContext.refIndexList

    val roots = nav.createRootsIterator()
    nominatedInstances.clear()

    val nominatedClassNames = config.perClassOptions.classNames
    nominatedClassNames.forEach {
      nominatedInstances.put(classStore.get(it), IntOpenHashSet())
    }

    progress.text2 = DiagnosticBundle.message("analyze.graph.progress.details.collect.roots")

    var toVisit = IntArrayList()
    var toVisit2 = IntArrayList()

    val rootsSet = IntOpenHashSet()
    val frameRootsSet = IntOpenHashSet()

    // Mark all roots to be visited, set them as their own parents
    while (roots.hasNext()) {
      val rootObject = roots.next()
      val rootObjectId = rootObject.id.toInt()
      if (rootObject.reason.javaFrame) {
        frameRootsSet.add(rootObjectId)
      }
      else {
        addIdToSetIfOrphan(rootsSet, rootObjectId)
      }
    }

    if (analysisContext.config.traverseOptions.includeClassesAsRoots) {
      // Mark all class object as to be visited, set them as their own parents
      classStore.forEachClass { classDefinition ->
        addIdToSetIfOrphan(rootsSet, classDefinition.id.toInt())
        classDefinition.objectStaticFields.forEach { staticField ->
          addIdToSetIfOrphan(rootsSet, staticField.value.toInt())
        }
        classDefinition.constantFields.forEach { objectId ->
          addIdToSetIfOrphan(rootsSet, objectId.toInt())
        }
      }
    }

    toVisit.addAll(rootsSet)
    rootsSet.clear()
    rootsSet.trim()

    var leafCounter = 0
    result.appendLine("Roots count: ${toVisit.size}")
    result.appendLine("Classes count: ${classStore.size()}")

    progress.text2 = DiagnosticBundle.message("analyze.graph.progress.details.traversing.instance.graph")

    val strongRefHistogramEntries = HashMap<ClassDefinition, HistogramVisitor.InternalHistogramEntry>()
    val reachableNonStrongHistogramEntries = HashMap<ClassDefinition, HistogramVisitor.InternalHistogramEntry>()
    val softReferenceIdToParentMap = Int2IntOpenHashMap()
    val weakReferenceIdToParentMap = Int2IntOpenHashMap()

    var visitedInstancesCount = 0
    val stopwatch = Stopwatch.createStarted()
    val references = LongArrayList()

    var visitedCount = 0
    var strongRefVisitedCount = 0
    var softWeakVisitedCount = 0

    var finalizableBytes = 0L
    var softBytes = 0L
    var weakBytes = 0L

    var phase = WalkGraphPhase.StrongReferencesNonLocalVariables // initial state

    val cleanerObjects = IntArrayList()
    val sunMiscCleanerClass = classStore.getClassIfExists("sun.misc.Cleaner")
    val finalizerClass = classStore.getClassIfExists("java.lang.ref.Finalizer")
    val onlyStrongReferences = config.traverseOptions.onlyStrongReferences

    while (!toVisit.isEmpty) {
      for (i in 0 until toVisit.size) {
        val id = toVisit.getInt(i)
        nav.goTo(id.toLong(), ObjectNavigator.ReferenceResolution.ALL_REFERENCES)
        val currentObjectClass = nav.getClass()

        if ((currentObjectClass == sunMiscCleanerClass || currentObjectClass == finalizerClass)
            && phase < WalkGraphPhase.CleanerFinalizerReferences) {
          if (!onlyStrongReferences) {
            // Postpone visiting sun.misc.Cleaner and java.lang.ref.Finalizer objects until later phase
            cleanerObjects.add(id)
          }
          continue
        }

        visitedInstancesCount++
        nominatedInstances[currentObjectClass]?.add(id)

        var isLeaf = true
        nav.copyReferencesTo(references)
        val currentObjectIsArray = currentObjectClass.isArray()

        if (phase < WalkGraphPhase.SoftReferences && nav.getSoftReferenceId() != 0L) {
          // Postpone soft references
          if (!onlyStrongReferences) {
            softReferenceIdToParentMap.put(nav.getSoftReferenceId().toInt(), id)
          }
          references[nav.getSoftWeakReferenceIndex()] = 0L
        }

        if (phase < WalkGraphPhase.WeakReferences && nav.getWeakReferenceId() != 0L) {
          // Postpone weak references
          if (!onlyStrongReferences) {
            weakReferenceIdToParentMap.put(nav.getWeakReferenceId().toInt(), id)
          }
          references[nav.getSoftWeakReferenceIndex()] = 0L
        }

        for (j in 0 until references.size) {
          val referenceId = references.getLong(j).toInt()

          if (addIdToListAndSetParentIfOrphan(toVisit2, referenceId, id)) {
            if (!currentObjectIsArray && j <= 254) {
              refIndexList[referenceId] = j + 1
            }
            isLeaf = false
          }
        }

        visitedList[visitedCount++] = id
        val size = nav.getObjectSize()
        var sizeDivBy4 = (size + 3) / 4
        if (sizeDivBy4 == 0) sizeDivBy4 = 1
        sizesList[id] = sizeDivBy4

        var histogramEntries: HashMap<ClassDefinition, HistogramVisitor.InternalHistogramEntry>
        if (phase == WalkGraphPhase.StrongReferencesNonLocalVariables || phase == WalkGraphPhase.StrongReferencesLocalVariables) {
          histogramEntries = strongRefHistogramEntries
          if (isLeaf) {
            leafCounter++
          }
          strongRefVisitedCount++
        }
        else {
          histogramEntries = reachableNonStrongHistogramEntries
          when (phase) {
            WalkGraphPhase.CleanerFinalizerReferences -> {
              finalizableBytes += size
            }
            WalkGraphPhase.SoftReferences -> {
              softBytes += size
            }
            else -> {
              assert(phase == WalkGraphPhase.WeakReferences)
              weakBytes += size
            }
          }
          softWeakVisitedCount++
        }
        histogramEntries.getOrPut(currentObjectClass) {
          HistogramVisitor.InternalHistogramEntry(currentObjectClass)
        }.addInstance(size.toLong())
      }
      progress.fraction = (1.0 * visitedInstancesCount / nav.instanceCount)
      toVisit.clear()
      val tmp = toVisit
      toVisit = toVisit2
      toVisit2 = tmp

      // Handle state transitions
      while (toVisit.size == 0 && phase != WalkGraphPhase.Finished) {
        // Next state
        phase = WalkGraphPhase.values()[phase.ordinal + 1]

        when (phase) {
          WalkGraphPhase.StrongReferencesLocalVariables ->
            frameRootsSet.forEach { id ->
              addIdToListAndSetParentIfOrphan(toVisit, id, id)
            }
          WalkGraphPhase.CleanerFinalizerReferences -> {
            toVisit.addAll(cleanerObjects)
            cleanerObjects.clear()
          }
          WalkGraphPhase.SoftReferences -> {
            for (entry in softReferenceIdToParentMap.int2IntEntrySet().fastIterator()) {
              addIdToListAndSetParentIfOrphan(toVisit, entry.intKey, entry.intValue)
            }
            // No need to store the list anymore
            softReferenceIdToParentMap.clear()
            softReferenceIdToParentMap.trim()
          }
          WalkGraphPhase.WeakReferences -> {
            for (entry in weakReferenceIdToParentMap.int2IntEntrySet().fastIterator()) {
              addIdToListAndSetParentIfOrphan(toVisit, entry.intKey, entry.intValue)
            }
            // No need to store the list anymore
            weakReferenceIdToParentMap.clear()
            weakReferenceIdToParentMap.trim()
          }
          else -> Unit // No work for other state transitions
        }
      }
    }
    assert(cleanerObjects.isEmpty)
    assert(softReferenceIdToParentMap.isEmpty())
    assert(weakReferenceIdToParentMap.isEmpty())

    result.appendLine("Finalizable size: ${toShortStringAsSize(finalizableBytes)}")
    result.appendLine("Soft-reachable size: ${toShortStringAsSize(softBytes)}")
    result.appendLine("Weak-reachable size: ${toShortStringAsSize(weakBytes)}")

    strongRefHistogram = Histogram(
      strongRefHistogramEntries
        .values
        .map { it.asHistogramEntry() }
        .sortedByDescending { it.totalInstances },
      strongRefVisitedCount.toLong())

    softWeakRefHistogram = Histogram(
      reachableNonStrongHistogramEntries
        .values
        .map { it.asHistogramEntry() }
        .sortedByDescending { it.totalInstances },
      softWeakVisitedCount.toLong())

    val stopwatchUpdateSizes = Stopwatch.createStarted()
    // Update sizes for non-leaves
    var index = visitedCount - 1
    while (index >= 0) {
      val id = visitedList[index]
      val parentId = parentList[id]
      if (id != parentId) {
        sizesList[parentId] += sizesList[id]
      }
      index--
    }
    stopwatchUpdateSizes.stop()

    if (config.metaInfoOptions.include) {
      result.appendLine("Analysis completed! Visited instances: $visitedInstancesCount, time: $stopwatch")
      result.appendLine("Update sizes time: $stopwatchUpdateSizes")
      result.appendLine("Leaves found: $leafCounter")
    }
    return result.toString()
  }

  /**
   * Adds object id to the list, only if the object does not have a parent object. Object will
   * also have a parent assigned.
   * For root objects, parentId should point back to the object making the object its own parent.
   * For other cases parentId can be provided.
   *
   * @return true if object was added to the list.
   */
  private fun addIdToListAndSetParentIfOrphan(list: IntList, id: Int, parentId: Int = id): Boolean {
    if (id != 0 && getParentIdForObjectId(id.toLong()) == 0L) {
      setParentForObjectId(id.toLong(), parentId.toLong())
      list.add(id)
      return true
    }
    return false
  }

  /**
   * Adds object id to the set, only if the object does not have a parent object and is not yet in the set.
   * Object will also have a parent assigned.
   * For root objects, parentId should point back to the object making the object its own parent.
   * For other cases parentId can be provided.
   *
   * @return true if object was added to the set.
   */
  private fun addIdToSetIfOrphan(set: IntSet, id: Int, parentId: Int = id): Boolean {
    if (id != 0 && getParentIdForObjectId(id.toLong()) == 0L && set.add(id)) {
      setParentForObjectId(id.toLong(), parentId.toLong())
      return true
    }
    return false
  }

  private fun getAndClearStrongRefHistogram(): Histogram {
    val result = strongRefHistogram
    strongRefHistogram = null
    return result ?: throw IllegalStateException("Graph not analyzed.")
  }

  private fun getAndClearSoftWeakHistogram(): Histogram {
    val result = softWeakRefHistogram
    softWeakRefHistogram = null
    return result ?: throw IllegalStateException("Graph not analyzed.")
  }


}
