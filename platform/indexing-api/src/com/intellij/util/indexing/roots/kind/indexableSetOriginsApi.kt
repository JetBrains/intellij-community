// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.kind

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.roots.IndexableFilesIterator
import java.util.*
import java.util.function.Predicate

/**
 * Represents an origin of [com.intellij.util.indexing.roots.IndexableFilesIterator].
 */
interface IndexableSetOrigin

/**
 * Represents an origin of [com.intellij.util.indexing.roots.IndexableFilesIterator] which has enough info to create iterator on itself.
 * Designed for use with [com.intellij.util.indexing.IndexableFilesIndex] in case it's enabled.
 * See [com.intellij.util.indexing.IndexableFilesIndex.shouldBeUsed]
 */
abstract class IndexableSetIterableOrigin : IndexableSetOrigin {
  abstract val iterationRoots: Collection<VirtualFile>
  protected abstract val exclusionData: ExclusionData

  fun isExcluded(file: VirtualFile): Boolean = exclusionData.isExcluded(file)

  abstract fun createIterator(): IndexableFilesIterator

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as IndexableSetIterableOrigin

    if (iterationRoots != other.iterationRoots) return false
    if (exclusionData != other.exclusionData) return false

    return true
  }

  override fun hashCode(): Int {
    var result = iterationRoots.hashCode()
    result = 31 * result + exclusionData.hashCode()
    return result
  }

  interface ExclusionData : Predicate<VirtualFile> {

    fun isExcluded(file: VirtualFile): Boolean

    fun addRelevantExcludedRoots(allExcludedRoots: Collection<VirtualFile>,
                                 strictlyUnderIncludedRoots: Boolean)

    fun setExcludedRootsFromChildContentRoots(childContentRoots: Collection<VirtualFile>)

    fun addExcludedFileCondition(condition: Condition<VirtualFile>?)

    fun load(data: ExclusionData)

    override fun test(file: VirtualFile): Boolean = isExcluded(file)

    companion object {
      fun createExclusionData(includedRoots: Collection<VirtualFile>): ExclusionData = ExclusionDataImpl(includedRoots)

      private fun isUnderFiles(file: VirtualFile, roots: Collection<VirtualFile?>, strictly: Boolean): Boolean {
        if (roots.isEmpty()) return false
        var parent: VirtualFile? = if (strictly) file.parent else file
        while (parent != null) {
          if (roots.contains(parent)) {
            return true
          }
          parent = parent.parent
        }
        return false
      }

      private class ExclusionDataImpl(private val includedRoots: Collection<VirtualFile>) : ExclusionData {
        private val excludedRoots: MutableCollection<VirtualFile> = mutableListOf()
        private var excludeCondition: Predicate<VirtualFile>? = null
        private val excludedChildContentRoots: MutableCollection<VirtualFile> = mutableListOf()

        init {
          for (includedRoot in includedRoots) {
            Objects.requireNonNull(includedRoot)
          }
        }

        override fun addRelevantExcludedRoots(allExcludedRoots: Collection<VirtualFile>,
                                              strictlyUnderIncludedRoots: Boolean) {
          addFilesUnder(allExcludedRoots, excludedRoots, strictlyUnderIncludedRoots)
        }

        private fun addFilesUnder(allExcludedRoots: Collection<VirtualFile>,
                                  target: MutableCollection<VirtualFile>,
                                  strictly: Boolean) {
          for (excludedRoot in allExcludedRoots) {
            Objects.requireNonNull(excludedRoot)
            if (isUnderFiles(excludedRoot, includedRoots, strictly)) {
              target.add(excludedRoot)
            }
          }
        }

        override fun setExcludedRootsFromChildContentRoots(childContentRoots: Collection<VirtualFile>) {
          excludedChildContentRoots.clear()
          addFilesUnder(childContentRoots, excludedChildContentRoots, false)
        }

        override fun addExcludedFileCondition(condition: Condition<VirtualFile>?) {
          if (condition == null) return
          val predicate = Predicate<VirtualFile> { condition.value(it) }
          excludeCondition?.also { excludeCondition = it.or(predicate) } ?: run {
            excludeCondition = predicate
          }
        }

        override fun isExcluded(file: VirtualFile): Boolean {
          val excludedRoots = excludedRoots + excludedChildContentRoots
          val condition = excludeCondition
          if (condition == null && excludedRoots.isEmpty()) return false
          var tested: VirtualFile? = file
          while (tested != null) {
            if (excludedRoots.contains(tested)) return true
            if (condition?.test(tested) == true) return true
            if (includedRoots.contains(tested)) return false
            tested = tested.parent
          }
          return false
        }

        override fun load(data: ExclusionData) {
          assert(data is ExclusionDataImpl) { "Exclusion data is expected to be the same" }
          excludedRoots.clear()
          excludedRoots.addAll((data as ExclusionDataImpl).excludedRoots)
          excludeCondition = data.excludeCondition
          excludedChildContentRoots.clear()
          excludedChildContentRoots.addAll(data.excludedChildContentRoots)
        }

        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false

          other as ExclusionDataImpl

          if (includedRoots != other.includedRoots) return false
          if (excludedRoots != other.excludedRoots) return false
          //we can't compare conditions, so !== for the sake of reflexivity
          if (excludeCondition !== other.excludeCondition) return false
          if (excludedChildContentRoots != other.excludedChildContentRoots) return false

          return true
        }

        override fun hashCode(): Int {
          var result = includedRoots.hashCode()
          result = 31 * result + excludedRoots.hashCode()
          result = 31 * result + (excludeCondition?.hashCode() ?: 0)
          result = 31 * result + excludedChildContentRoots.hashCode()
          return result
        }
      }

      fun getDummyExclusionData(): ExclusionData = DummyExclusionData

      private object DummyExclusionData : ExclusionData {
        override fun isExcluded(file: VirtualFile): Boolean = false

        override fun addRelevantExcludedRoots(allExcludedRoots: Collection<VirtualFile>,
                                              strictlyUnderIncludedRoots: Boolean) {
        }

        override fun setExcludedRootsFromChildContentRoots(childContentRoots: Collection<VirtualFile>) {
        }

        override fun addExcludedFileCondition(condition: Condition<VirtualFile>?) {
        }

        override fun load(data: ExclusionData) {
          assert(data == DummyExclusionData) { "Can't load non dummy exclusion data into a dummy one" }
        }
      }
    }
  }
}

interface ModuleRootOrigin : IndexableSetOrigin {
  val module: Module
  val roots: List<VirtualFile>
}

interface LibraryOrigin : IndexableSetOrigin {
  val classRoots: List<VirtualFile>
  val sourceRoots: List<VirtualFile>
}

interface SyntheticLibraryOrigin : IndexableSetOrigin {
  val syntheticLibrary: SyntheticLibrary
  val rootsToIndex: Collection<VirtualFile>
}

interface SdkOrigin : IndexableSetOrigin {
  val sdk: Sdk
  val rootsToIndex: Collection<VirtualFile>
}

interface IndexableSetContributorOrigin : IndexableSetOrigin {
  val indexableSetContributor: IndexableSetContributor
  val rootsToIndex: Set<VirtualFile>
}

interface ProjectFileOrDirOrigin : IndexableSetOrigin {
  val fileOrDir: VirtualFile
}
