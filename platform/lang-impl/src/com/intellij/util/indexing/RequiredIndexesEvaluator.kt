// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.fileTypes.FileType
import com.jetbrains.rd.util.concurrentMapOf
import java.util.function.Predicate

internal class RequiredIndexesEvaluator(private val registeredIndexes: RegisteredIndexes) {
  private interface IndexedFilePredicate : Predicate<IndexedFile>

  private val truePredicate = object : IndexedFilePredicate {
    override fun test(t: IndexedFile): Boolean = true
  }

  private fun combinePredicates(p1: IndexedFilePredicate, p2: IndexedFilePredicate): IndexedFilePredicate {
    if (p1 == truePredicate) return p2
    else if (p2 == truePredicate) return p1
    else return object : IndexedFilePredicate {
      override fun test(t: IndexedFile): Boolean = p1.test(t) && p2.test(t)
    }
  }

  private inner class HintAwareIndexList {
    private val sureIndexIds: List<ID<*, *>>
    private val unsureIndexIds: List<Pair<ID<*, *>, IndexedFilePredicate>>

    init {
      FileBasedIndexImpl.LOG.assertTrue(registeredIndexes.isInitialized, "RegisteredIndexes are not initialized")
    }

    constructor(unsureIndexIds: Collection<ID<*, *>>) {
      val sure: MutableList<ID<*, *>> = mutableListOf<ID<*, *>>()
      val unsure: MutableList<Pair<ID<*, *>, IndexedFilePredicate>> = mutableListOf()
      this.sureIndexIds = sure
      this.unsureIndexIds = unsure

      for (unsureIndexId in unsureIndexIds) {
        val predicate = acceptsInput(unsureIndexId)
        if (predicate == truePredicate) sure.add(unsureIndexId)
        else unsure.add(Pair(unsureIndexId, predicate))
      }
    }

    fun getRequiredIndexes(indexedFile: IndexedFile): List<ID<*, *>> {
      if (unsureIndexIds.isEmpty()) return sureIndexIds;

      FileBasedIndexImpl.LOG.assertTrue(indexedFile.project != null, "Should not index files from unknown project")
      val acceptedCandidates: MutableList<ID<*, *>> = ArrayList(sureIndexIds)
      for ((indexId, filter) in unsureIndexIds) {
        if (filter.test(indexedFile)) {
          acceptedCandidates.add(indexId)
        }
      }
      return acceptedCandidates
    }
  }

  private val indexesForFileType: MutableMap<FileType, HintAwareIndexList> = concurrentMapOf()
  private val indexesForDirectories: HintAwareIndexList = HintAwareIndexList(registeredIndexes.indicesForDirectories)
  private val contentlessIndexes: HintAwareIndexList = HintAwareIndexList(registeredIndexes.notRequiringContentIndices)
  private fun getState(): IndexConfiguration = registeredIndexes.configurationState
  private fun getInputFilter(indexId: ID<*, *>): FileBasedIndex.InputFilter = getState().getInputFilter(indexId)

  private fun acceptsInput(indexId: ID<*, *>): IndexedFilePredicate {
    val indexAccepts = getIndexerFilter(indexId)
    val globalAccepts = getGlobalFilter(indexId)
    return combinePredicates(indexAccepts, globalAccepts)
  }

  private fun getIndexerFilter(indexId: ID<*, *>) = object : IndexedFilePredicate {
    override fun test(indexedFile: IndexedFile): Boolean {
      val filter: FileBasedIndex.InputFilter = getInputFilter(indexId)
      return FileBasedIndexEx.acceptsInput(filter, indexedFile)
    }
  }

  private fun getGlobalFilter(indexId: ID<*, *>) = object : IndexedFilePredicate {
    override fun test(indexedFile: IndexedFile): Boolean {
      return !GlobalIndexFilter.isExcludedFromIndexViaFilters(indexedFile.file, indexId, indexedFile.project)
    }
  }

  fun getRequiredIndexes(indexedFile: IndexedFile): List<ID<*, *>> {
    if (indexedFile.file.isDirectory) {
      return getRequiredIndexesForDirectories(indexedFile)
    }
    else {
      return getRequiredIndexesForRegularFiles(indexedFile)
    }
  }

  private fun getRequiredIndexesForRegularFiles(indexedFile: IndexedFile): List<ID<*, *>> {
    var fileType = indexedFile.fileType
    if (fileType is SubstitutedFileType) {
      fileType = fileType.fileType
    }

    if (FileBasedIndexImpl.isProjectOrWorkspaceFile(indexedFile.file, fileType)) {
      return contentlessIndexes.getRequiredIndexes(indexedFile)
    }
    else {
      var filteredResults = indexesForFileType[fileType]
      if (filteredResults != null) return filteredResults.getRequiredIndexes(indexedFile)

      filteredResults = HintAwareIndexList(getState().getFileTypesForIndex(fileType))
      indexesForFileType[fileType] = filteredResults
      return filteredResults.getRequiredIndexes(indexedFile)
    }
  }

  private fun getRequiredIndexesForDirectories(indexedFile: IndexedFile): List<ID<*, *>> {
    if (FileBasedIndexImpl.isProjectOrWorkspaceFile(indexedFile.file, null)) {
      return emptyList()
    }
    else {
      return indexesForDirectories.getRequiredIndexes(indexedFile)
    }
  }
}