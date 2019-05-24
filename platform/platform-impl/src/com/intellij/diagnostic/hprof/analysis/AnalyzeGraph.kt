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
import com.intellij.diagnostic.hprof.histogram.Histogram
import com.intellij.diagnostic.hprof.navigator.ObjectNavigator
import com.intellij.diagnostic.hprof.util.IntList
import com.intellij.diagnostic.hprof.util.PartialProgressIndicator
import com.intellij.diagnostic.hprof.visitors.HistogramVisitor
import com.google.common.base.Stopwatch
import com.intellij.openapi.progress.ProgressIndicator
import gnu.trove.TIntArrayList
import gnu.trove.TIntHashSet
import gnu.trove.TLongArrayList

class AnalyzeGraph(private val nav: ObjectNavigator,
                   private val parentList: IntList,
                   private val sizesList: IntList,
                   private val visitedList: IntList,
                   private val nominatedClassNames: Set<String>,
                   private val includeMetaInfo: Boolean) {
  private fun ObjectNavigator.getParentId(): Long = getParentIdForObjectId(id)

  private var strongRefHistogram: Histogram? = null

  private fun setParentForObjectId(objectId: Long, parentId: Long) {
    parentList[objectId.toInt()] = parentId.toInt()
  }

  private fun getParentIdForObjectId(objectId: Long): Long {
    return parentList[objectId.toInt()].toLong()
  }

  private val nominatedInstances = HashMap<ClassDefinition, TIntHashSet>()

  fun analyze(progress: ProgressIndicator): String {
    val result = StringBuilder()
    val roots = nav.createRootsIterator()
    nominatedInstances.clear()

    nominatedClassNames.forEach {
      nominatedInstances[nav.classStore[it]] = TIntHashSet()
    }

    progress.text2 = "Collect all object roots"

    var toVisit = TIntArrayList()
    var toVisit2 = TIntArrayList()

    // Mark all class object as to be visited, set them as their own parents
    nav.classStore.forEachClass { classDefinition ->
      if (getParentIdForObjectId(classDefinition.id) == 0L) {
        toVisit.add(classDefinition.id.toInt())
        setParentForObjectId(classDefinition.id, classDefinition.id)
      }
      classDefinition.staticFields.forEach {
        val objectId = it.objectId
        if (objectId != 0L) {
          toVisit.add(objectId.toInt())
          setParentForObjectId(objectId, objectId)
        }
      }
      classDefinition.constantFields.forEach { objectId ->
        if (objectId != 0L) {
          toVisit.add(objectId.toInt())
          setParentForObjectId(objectId, objectId)
        }
      }
    }

    var leafCounter = 0
    // Mark all roots to be visited, set them as their own parents
    while (roots.hasNext()) {
      val rootObjectId = roots.next()
      nav.goTo(rootObjectId)
      if (nav.getParentId() == 0L) {
        toVisit.add(nav.id.toInt())
        setParentForObjectId(nav.id, nav.id)
      }
    }
    result.appendln("Roots count: ${toVisit.size()}")
    result.appendln("Classes count: ${nav.classStore.size()}")

    progress.text2 = "Walking object graph"

    val strongRefHistogramEntires = HashMap<ClassDefinition, HistogramVisitor.InternalHistogramEntry>()

    val walkProgress = PartialProgressIndicator(progress, 0.1, 0.5)
    var visitedInstancesCount = 0
    val stopwatch = Stopwatch.createStarted()
    val references = TLongArrayList()
    var visitedCount = 0
    while (!toVisit.isEmpty) {
      for (i in 0 until toVisit.size()) {
        val id = toVisit[i]
        nav.goTo(id.toLong())
        visitedInstancesCount++

        nav.copyReferencesTo(references)

        val currentObjectClass = nav.getClass()
        nominatedInstances[currentObjectClass]?.add(id)

        var isLeaf = true
        for (j in 0 until references.size()) {
          val it = references[j]
          if (it != 0L && getParentIdForObjectId(it) == 0L) {
            setParentForObjectId(it, id.toLong())
            toVisit2.add(it.toInt())
            isLeaf = false
          }
        }
        visitedList[visitedCount++] = id
        val size = nav.getObjectSize()
        var sizeDivBy4 = (nav.getObjectSize() + 3) / 4
        if (sizeDivBy4 == 0) sizeDivBy4 = 1
        sizesList[id] = sizeDivBy4
        strongRefHistogramEntires.getOrPut(currentObjectClass) {
          HistogramVisitor.InternalHistogramEntry(currentObjectClass)
        }.addInstance(size.toLong())
        if (isLeaf) leafCounter++
      }
      walkProgress.fraction = (1.0 * visitedInstancesCount / nav.instanceCount)
      toVisit.resetQuick()
      val tmp = toVisit
      toVisit = toVisit2
      toVisit2 = tmp
    }
    strongRefHistogram = Histogram(
      strongRefHistogramEntires
        .values
        .map { it.asHistogramEntry() }
        .sortedByDescending { it.totalInstances },
      visitedCount.toLong())

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

    if (includeMetaInfo) {
      result.appendln("Analysis completed! Visited instances: $visitedInstancesCount, time: $stopwatch")
      result.appendln("Update sizes time: $stopwatchUpdateSizes")
      result.appendln("Leaves found: $leafCounter")
    }
    return result.toString()
  }

  fun prepareReport(progress: ProgressIndicator,
                    disposedObjectIDs: TIntHashSet): String {
    val result = StringBuilder()

    var counter = 0
    val stopwatch = Stopwatch.createUnstarted()
    nominatedInstances.forEach { classDefinition, set ->
      progress.fraction = counter.toDouble() / nominatedInstances.size
      progress.text2 = "Processing: ${set.size()} ${classDefinition.prettyName}"
      stopwatch.reset().start()
      result.appendln()
      result.appendln("CLASS: ${classDefinition.prettyName} (${set.size()} objects)")
      val referenceRegistry = GCRootPathsTree(disposedObjectIDs, parentList, sizesList, nav, classDefinition)
      set.forEach { objectId ->
        referenceRegistry.registerObject(objectId)
        true
      }
      result.append(referenceRegistry.printTree(100, 25))
      if (includeMetaInfo) {
        result.appendln("Report for ${classDefinition.prettyName} created in $stopwatch")
      }
      counter++
    }
    progress.fraction = 1.0
    return result.toString()
  }

  fun getStrongRefHistogram(): Histogram {
    return strongRefHistogram ?: throw IllegalStateException("Graph not analyzed.")
  }


}
