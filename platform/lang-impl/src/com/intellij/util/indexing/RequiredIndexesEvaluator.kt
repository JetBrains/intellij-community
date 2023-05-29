// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.search.FileTypeIndex
import com.intellij.util.ThreeState
import com.intellij.util.indexing.hints.FileTypeIndexingHint
import com.intellij.util.indexing.hints.FileTypeInputFilterPredicate
import com.intellij.util.indexing.hints.IndexingHint
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

  private fun andPredicates(p1: IndexedFilePredicate, p2: IndexedFilePredicate): IndexedFilePredicate {
    if (p1 == falsePredicate) return p1
    else if (p2 == falsePredicate) return p2
    else if (p1 == truePredicate) return p2
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

    constructor(indexIds: Collection<ID<*, *>>, fileType: FileType? = null) {
      val sure: MutableList<ID<*, *>> = mutableListOf()
      val unsure: MutableList<Pair<ID<*, *>, IndexedFilePredicate>> = mutableListOf()
      this.sureIndexIds = sure
      this.unsureIndexIds = unsure
      for (indexId in indexIds) {
        val predicate = acceptsInput(indexId, fileType)
        if (predicate == truePredicate) sure.add(indexId)
        else if (predicate != falsePredicate) unsure.add(Pair(indexId, predicate))
      }
      LOG.debug("fileType: $fileType, slow scanning path via indexes: ${unsure.map { it.first.name }.toList()}")
    }

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
  private val indexesForDirectories: HintAwareIndexList = HintAwareIndexList(registeredIndexes.indicesForDirectories)
  private fun getState(): IndexConfiguration = registeredIndexes.configurationState
  private fun getInputFilter(indexId: ID<*, *>): FileBasedIndex.InputFilter = getState().getInputFilter(indexId)

  private fun acceptsInput(indexId: ID<*, *>, fileType: FileType?): IndexedFilePredicate {
    val hint = toHint(getInputFilter(indexId))
    val globalHint = getGlobalHint(indexId)

    val indexerHintPredicate = applyHints(hint, fileType)
    val globalHintPredicate = applyHints(globalHint, fileType)

    return andPredicates(indexerHintPredicate, globalHintPredicate)
  }

  private fun toHint(filter: FileBasedIndex.InputFilter): IndexingHint {
    return if (filter is IndexingHint) {
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
      object : IndexingHint {
        override fun whenAllOtherHintsUnsure(file: IndexedFile): Boolean {
          return FileBasedIndexEx.acceptsInput(filter, file)
        }
      }
    }
  }

  private fun getGlobalHint(indexId: ID<*, *>): IndexingHint = object : IndexingHint {
    override fun whenAllOtherHintsUnsure(file: IndexedFile): Boolean {
      return !GlobalIndexFilter.isExcludedFromIndexViaFilters(file.file, indexId, file.project)
    }
  }

  private fun applyHints(indexingHint: IndexingHint, fileType: FileType?): IndexedFilePredicate {
    val hint = applyFileTypeHint(indexingHint, fileType)
    return when (hint) {
      ThreeState.YES -> truePredicate
      ThreeState.NO -> falsePredicate
      ThreeState.UNSURE -> object : IndexedFilePredicate {
        override fun test(indexedFile: IndexedFile): Boolean = indexingHint.whenAllOtherHintsUnsure(indexedFile)
      }
    }
  }

  private fun applyFileTypeHint(indexingHint: IndexingHint, fileType: FileType?): ThreeState {
    if (fileType != null && indexingHint is FileTypeIndexingHint) {
      return indexingHint.hintAcceptFileType(fileType)
    }
    else {
      return ThreeState.UNSURE
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

      filteredResults = HintAwareIndexList(getState().getFileTypesForIndex(substitutedFileType), fileType)
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