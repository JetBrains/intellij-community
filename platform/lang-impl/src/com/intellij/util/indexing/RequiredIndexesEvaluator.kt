// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.fileTypes.FileType
import com.jetbrains.rd.util.concurrentMapOf

internal class RequiredIndexesEvaluator(private val registeredIndexes: RegisteredIndexes) {
  private inner class HintAwareIndexList {
    private val sureIndexIds: List<ID<*, *>>
    private val unsureIndexIds: List<Pair<ID<*, *>, ((IndexedFile) -> Boolean)>>

    init {
      FileBasedIndexImpl.LOG.assertTrue(registeredIndexes.isInitialized, "RegisteredIndexes are not initialized")
    }

    constructor(sureIndexIds: List<ID<*, *>>, unsureIndexIds: Collection<ID<*, *>>) {
      this.sureIndexIds = sureIndexIds
      val unsure: MutableList<Pair<ID<*, *>, ((IndexedFile) -> Boolean)>> = mutableListOf()
      this.unsureIndexIds = unsure

      for (unsureIndexId in unsureIndexIds) {
        unsure.add(Pair(unsureIndexId) { acceptsInput(unsureIndexId, it) })
      }
    }

    fun getRequiredIndexes(indexedFile: IndexedFile): List<ID<*, *>> {
      if (unsureIndexIds.isEmpty()) return sureIndexIds;

      val acceptedCandidates: MutableList<ID<*, *>> = ArrayList(sureIndexIds)
      for ((indexId, filter) in unsureIndexIds) {
        if (filter(indexedFile)) {
          acceptedCandidates.add(indexId)
        }
      }
      return acceptedCandidates
    }
  }

  private val indexesForFileType: MutableMap<FileType, HintAwareIndexList> = concurrentMapOf()
  private val indexesForDirectories: HintAwareIndexList = HintAwareIndexList(emptyList(), registeredIndexes.indicesForDirectories)
  private val contentlessIndexes: HintAwareIndexList = HintAwareIndexList(emptyList(), registeredIndexes.notRequiringContentIndices)
  private fun getState(): IndexConfiguration = registeredIndexes.configurationState
  private fun getInputFilter(indexId: ID<*, *>): FileBasedIndex.InputFilter = getState().getInputFilter(indexId)

  private fun acceptsInput(indexId: ID<*, *>, indexedFile: IndexedFile): Boolean {
    val filter: FileBasedIndex.InputFilter = getInputFilter(indexId)
    if (!FileBasedIndexEx.acceptsInput(filter, indexedFile)) return false
    FileBasedIndexImpl.LOG.assertTrue(indexedFile.project != null, "Should not index files from unknown project")
    return !GlobalIndexFilter.isExcludedFromIndexViaFilters(indexedFile.file, indexId, indexedFile.project)
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

      filteredResults = HintAwareIndexList(emptyList(), getState().getFileTypesForIndex(fileType))
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