// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.search.FileTypeIndex
import com.intellij.util.ThreeState
import com.intellij.util.indexing.hints.FileTypeIndexingHint
import com.intellij.util.indexing.hints.FileTypeInputFilterPredicate
import com.intellij.util.indexing.hints.RejectAllIndexingHint
import com.jetbrains.rd.util.concurrentMapOf
import java.util.function.Predicate

internal class RequiredIndexesEvaluator(private val registeredIndexes: RegisteredIndexes) {
  private interface IndexedFilePredicate : Predicate<IndexedFile>

  private val truePredicate = object : IndexedFilePredicate {
    override fun test(t: IndexedFile): Boolean = true
  }

  private val falsePredicate = object : IndexedFilePredicate {
    override fun test(t: IndexedFile): Boolean = false
  }

  init {
    FileBasedIndexImpl.LOG.assertTrue(registeredIndexes.isInitialized, "RegisteredIndexes are not initialized")
  }

  private fun andPredicates(p1: IndexedFilePredicate, p2: IndexedFilePredicate): IndexedFilePredicate {
    if (p1 == falsePredicate) return p1
    else if (p2 == falsePredicate) return p2
    else if (p1 == truePredicate) return p2
    else if (p2 == truePredicate) return p1
    else return object : IndexedFilePredicate {
      override fun test(t: IndexedFile): Boolean = p1.test(t) && p2.test(t)
    }
  }

  private fun indexesForDirectories(indexIds: Collection<ID<*, *>>): HintAwareIndexList {
    return buildHintAwareIndexList("Directories", indexIds) { indexId -> acceptDirectory(indexId) }
  }

  private fun indexesForRegularFiles(indexIds: Collection<ID<*, *>>, fileType: FileType): HintAwareIndexList {
    return buildHintAwareIndexList("FileType $fileType", indexIds) { indexId -> acceptRegularFile(indexId, fileType) }
  }

  private fun buildHintAwareIndexList(listDebugName: String,
                                      indexIds: Collection<ID<*, *>>,
                                      indexToPredicate: (ID<*, *>) -> IndexedFilePredicate): HintAwareIndexList {
    val sure: MutableList<ID<*, *>> = mutableListOf()
    val unsure: MutableList<Pair<ID<*, *>, IndexedFilePredicate>> = mutableListOf()
    for (indexId in indexIds) {
      val predicate = indexToPredicate(indexId)
      if (predicate == truePredicate) sure.add(indexId)
      else if (predicate != falsePredicate) unsure.add(Pair(indexId, predicate))
    }

    LOG.debug { "$listDebugName, will be indexed by: ${sure.map { it.name }.toList() + unsure.map { it.first.name }.toList()}" }
    if (unsure.isNotEmpty()) LOG.debug { "$listDebugName, slow scanning path via indexes: ${unsure.map { it.first.name }.toList()}" }

    return HintAwareIndexList(sure, unsure)
  }

  private class HintAwareIndexList(private val sureIndexIds: List<ID<*, *>>,
                                   private val unsureIndexIds: List<Pair<ID<*, *>, IndexedFilePredicate>>) {
    fun getRequiredIndexes(indexedFile: IndexedFile): List<ID<*, *>> {
      if (unsureIndexIds.isEmpty()) return sureIndexIds

      // IDEA-320788: this assertion is not correct. Currently, the project can be null in the following cases:
      //   1. VFS refreshed a file before scanning added it to per-project indexable files holder
      //   2. VFS refreshed a file that belonged to a project that already closed
      //   3. VFS refreshed a file that belongs to opened project, but excluded
      //FileBasedIndexImpl.LOG.assertTrue(indexedFile.project != null, "Should not index files from unknown project")
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
  private val indexesForDirectories: HintAwareIndexList = indexesForDirectories(registeredIndexes.indicesForDirectories)
  private fun getState(): IndexConfiguration = registeredIndexes.configurationState
  private fun getInputFilter(indexId: ID<*, *>): FileBasedIndex.InputFilter = getState().getInputFilter(indexId)

  private fun inputFilerToIndexedFilePredicate(inputFilter: FileBasedIndex.InputFilter, fileType: FileType): IndexedFilePredicate {
    val hint = toHint(inputFilter)
    return if (hint != null) {
      applyFileTypeHints(hint, fileType)
    }
    else {
      inputFilerToIndexedFilePredicate(inputFilter)
    }
  }

  private fun inputFilerToIndexedFilePredicate(inputFilter: FileBasedIndex.InputFilter): IndexedFilePredicate {
    if (inputFilter == RejectAllIndexingHint) {
      return falsePredicate
    }
    else {
      return object : IndexedFilePredicate {
        override fun test(indexedFile: IndexedFile): Boolean = FileBasedIndexEx.acceptsInput(inputFilter, indexedFile)
      }
    }
  }

  private fun acceptDirectory(indexId: ID<*, *>): IndexedFilePredicate {
    val inputFilter = getInputFilter(indexId)

    val indexerHintPredicate = inputFilerToIndexedFilePredicate(inputFilter)
    val globalHintPredicate = getGlobalIndexedFilePredicate(indexId)

    return andPredicates(indexerHintPredicate, globalHintPredicate)
  }

  private fun acceptRegularFile(indexId: ID<*, *>, fileType: FileType): IndexedFilePredicate {
    val inputFilter = getInputFilter(indexId)

    val indexerHintPredicate = inputFilerToIndexedFilePredicate(inputFilter, fileType)
    val globalHintPredicate = getGlobalIndexedFilePredicate(indexId)

    return andPredicates(indexerHintPredicate, globalHintPredicate)
  }

  private fun toHint(filter: FileBasedIndex.InputFilter): FileTypeIndexingHint? {
    return if (filter is FileTypeIndexingHint) {
      filter
    }
    else if (filter is DefaultFileTypeSpecificInputFilter &&
             // yes, we want to check exact class.
             // Optimization does not work for DefaultFileTypeSpecificInputFilter subtypes because subtypes can override acceptInput
             filter.javaClass == DefaultFileTypeSpecificInputFilter::class.java) {
      FileTypeInputFilterPredicate { fileType ->
        var matches = false
        filter.registerFileTypesUsedForIndexing { matches = (matches || it == fileType) }
        return@FileTypeInputFilterPredicate matches
      }
    }
    else {
      null
    }
  }

  private fun getGlobalIndexedFilePredicate(indexId: ID<*, *>): IndexedFilePredicate = object : IndexedFilePredicate {
    override fun test(file: IndexedFile): Boolean = !GlobalIndexFilter.isExcludedFromIndexViaFilters(file.file, indexId, file.project)
  }

  private fun applyFileTypeHints(indexingHint: FileTypeIndexingHint, fileType: FileType): IndexedFilePredicate {
    return when (indexingHint.hintAcceptFileType(fileType)) {
      ThreeState.YES -> truePredicate
      ThreeState.NO -> falsePredicate
      ThreeState.UNSURE -> object : IndexedFilePredicate {
        override fun test(indexedFile: IndexedFile): Boolean = indexingHint.whenFileTypeHintUnsure(indexedFile)
      }
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
    val fileType = indexedFile.fileType
    val substitutedFileType = (fileType as? SubstitutedFileType)?.fileType ?: fileType

    if (FileBasedIndexImpl.isProjectOrWorkspaceFile(indexedFile.file, substitutedFileType)) {
      return listOf(FileTypeIndex.NAME) // probably, we don't even need the filetype index
    }
    else {
      var filteredResults = indexesForFileType[fileType]
      if (filteredResults != null) return filteredResults.getRequiredIndexes(indexedFile)

      filteredResults = indexesForRegularFiles(getState().getFileTypesForIndex(substitutedFileType), fileType)
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

  companion object {
    private val LOG = logger<RequiredIndexesEvaluator>()
  }
}