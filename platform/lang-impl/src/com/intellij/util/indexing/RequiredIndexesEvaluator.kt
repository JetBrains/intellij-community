// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

internal class RequiredIndexesEvaluator(private val registeredIndexes: RegisteredIndexes) {
  private fun getState(): IndexConfiguration = registeredIndexes.configurationState
  private fun getInputFilter(indexId: ID<*, *>): FileBasedIndex.InputFilter = getState().getInputFilter(indexId)

  private fun acceptsInput(indexId: ID<*, *>, indexedFile: IndexedFile): Boolean {
    val filter: FileBasedIndex.InputFilter = getInputFilter(indexId)
    if (!FileBasedIndexEx.acceptsInput(filter, indexedFile)) return false
    FileBasedIndexImpl.LOG.assertTrue(indexedFile.project != null, "Should not index files from unknown project")
    return !GlobalIndexFilter.isExcludedFromIndexViaFilters(indexedFile.file, indexId, indexedFile.project)
  }

  fun getRequiredIndexes(indexedFile: IndexedFile): List<ID<*, *>> {
    val affectedIndexCandidates: List<ID<*, *>> = getAffectedIndexCandidates(indexedFile)
    val acceptedCandidates: MutableList<ID<*, *>> = ArrayList(affectedIndexCandidates.size)
    for (candidate in affectedIndexCandidates) {
      if (acceptsInput(candidate, indexedFile)) {
        acceptedCandidates.add(candidate)
      }
    }
    return acceptedCandidates
  }

  fun getAffectedIndexCandidates(indexedFile: IndexedFile): List<ID<*, *>> {
    if (indexedFile.file.isDirectory) {
      return if (FileBasedIndexImpl.isProjectOrWorkspaceFile(indexedFile.file, null)) emptyList<ID<*, *>>()
      else registeredIndexes.getIndicesForDirectories()
    }
    var fileType = indexedFile.fileType
    if (fileType is SubstitutedFileType) {
      fileType = fileType.fileType
    }
    return if (FileBasedIndexImpl.isProjectOrWorkspaceFile(indexedFile.file, fileType)) {
      registeredIndexes.notRequiringContentIndices.toList()
    }
    else {
      getState().getFileTypesForIndex(fileType)
    }
  }
}