// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.kind

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VfsUtilCore
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
    val rootsToExclude: Iterable<VirtualFile>

    fun isExcluded(file: VirtualFile): Boolean

    fun addRelevantExcludedRootsFromDirectoryIndexExcludePolicies(allExcludedRoots: Collection<VirtualFile>)

    fun setExcludedRootsFromChildContentRoots(childContentRoots: Collection<VirtualFile>)

    fun addExcludedFileCondition(condition: Condition<VirtualFile>?)

    fun load(data: ExclusionData)
    fun isExcludedByCondition(file: VirtualFile): Boolean

    override fun test(file: VirtualFile): Boolean = isExcluded(file)

    companion object {
      fun createExclusionData(includedRoots: Collection<VirtualFile>): ExclusionData = ExclusionDataImpl(includedRoots)

      private class ExclusionDataImpl(private val includedRoots: Collection<VirtualFile>) : ExclusionData {
        private val excludedRoots: MutableCollection<VirtualFile> = mutableListOf()
        private var excludeCondition: Predicate<VirtualFile>? = null
        private val excludedChildContentRoots: MutableCollection<VirtualFile> = mutableListOf()

        override fun addRelevantExcludedRootsFromDirectoryIndexExcludePolicies(allExcludedRoots: Collection<VirtualFile>) {
          for (excludedRoot in allExcludedRoots) {
            if (VfsUtilCore.isUnderFiles(excludedRoot, includedRoots)) {
              excludedRoots.add(excludedRoot)
            }
          }
        }

        override fun setExcludedRootsFromChildContentRoots(childContentRoots: Collection<VirtualFile>) {
          excludedChildContentRoots.clear()
          for (childContentRoot in childContentRoots) {
            if (VfsUtilCore.isUnderFiles(childContentRoot, includedRoots)) {
              excludedChildContentRoots.add(childContentRoot)
            }
          }
        }

        override fun addExcludedFileCondition(condition: Condition<VirtualFile>?) {
          if (condition == null) return
          val predicate = Predicate<VirtualFile> { condition.value(it) }
          excludeCondition?.also { excludeCondition = it.and(predicate) } ?: run {
            excludeCondition = predicate
          }
        }

        override fun isExcluded(file: VirtualFile): Boolean = VfsUtilCore.isUnderFiles(file, excludedRoots) ||
                                                              isExcludedByCondition(file) ||
                                                              VfsUtilCore.isUnderFiles(file, excludedChildContentRoots)

        override val rootsToExclude: Iterable<VirtualFile>
          get() = excludedRoots + excludedChildContentRoots


        override fun isExcludedByCondition(file: VirtualFile): Boolean = excludeCondition?.test(file) ?: false

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
          if (excludedChildContentRoots !== other.excludedChildContentRoots) return false

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
        override val rootsToExclude: Iterable<VirtualFile>
          get() = Collections.emptyList()

        override fun isExcluded(file: VirtualFile): Boolean = false
        override fun isExcludedByCondition(file: VirtualFile): Boolean = false
        override fun addRelevantExcludedRootsFromDirectoryIndexExcludePolicies(allExcludedRoots: Collection<VirtualFile>) {
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
