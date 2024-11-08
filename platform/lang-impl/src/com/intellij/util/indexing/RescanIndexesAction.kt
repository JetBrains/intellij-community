// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.actions.cache.*
import com.intellij.lang.LangBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.stubs.StubTreeBuilder
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.util.application
import com.intellij.util.indexing.dependencies.AppIndexingDependenciesService
import com.intellij.util.indexing.dependencies.FileIndexingStamp
import com.intellij.util.indexing.diagnostic.ProjectScanningHistory
import com.intellij.util.indexing.diagnostic.ScanningType
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.ProjectIndexableFilesIteratorImpl
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.*
import java.util.function.BiPredicate

@ApiStatus.Internal
class RescanIndexesAction : RecoveryAction {
  override val performanceRate: Int
    get() = 9990
  override val presentableName: @Nls(capitalization = Nls.Capitalization.Title) String
    get() = LangBundle.message("rescan.indexes.recovery.action.name")
  override val actionKey: String
    get() = "rescan"

  class ForceReindexingTrigger : BiPredicate<IndexedFile, FileIndexingStamp> {
    val stubAndIndexingStampInconsistencies: MutableList<CacheInconsistencyProblem> = Collections.synchronizedList(arrayListOf<CacheInconsistencyProblem>())

    private val stubIndex =
      runCatching { (FileBasedIndex.getInstance() as FileBasedIndexImpl).getIndex(StubUpdatingIndex.INDEX_ID) }
        .onFailure { logger<RescanIndexesAction>().error(it) }.getOrNull()

    private val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl

    private class StubAndIndexStampInconsistency(private val path: String) : CacheInconsistencyProblem {
      override val message: String
        get() = "`$path` should have already indexed stub but it's not present"
    }

    private fun isAbleToBuildStub(file: VirtualFile): Boolean = runCatching {
      StubTreeBuilder.buildStubTree(FileContentImpl.createByFile(file))
    }.getOrNull() != null

    override fun test(it: IndexedFile, indexingStamp: FileIndexingStamp): Boolean {
      if (stubIndex == null) {
        return false;
      }
      val fileId = (it.file as VirtualFileWithId).id
      if (fileBasedIndex.getIndexingState(it, stubIndex, indexingStamp) == FileIndexingState.UP_TO_DATE &&
          stubIndex.getIndexedFileData(fileId).isEmpty() &&
          isAbleToBuildStub(it.file)) {
        stubAndIndexingStampInconsistencies.add(StubAndIndexStampInconsistency(it.file.path))
        return true
      }
      return false
    }
  }

  override fun performSync(recoveryScope: RecoveryScope): List<CacheInconsistencyProblem> {
    val project = recoveryScope.project

    var predefinedIndexableFilesIterators: List<IndexableFilesIterator>? = null
    if (recoveryScope is FilesRecoveryScope) {
      predefinedIndexableFilesIterators = recoveryScope.files.map { ProjectIndexableFilesIteratorImpl(it) }
      if (predefinedIndexableFilesIterators.isEmpty()) return emptyList()
    }
    application.service<AppIndexingDependenciesService>().invalidateAllStamps("Rescanning indexes recovery action")
    val trigger = ForceReindexingTrigger()
    val parameters = CompletableDeferred(ScanningIterators(
      "Rescanning indexes recovery action",
      predefinedIndexableFilesIterators,
      null,
      if (predefinedIndexableFilesIterators == null) ScanningType.FULL_FORCED else ScanningType.PARTIAL_FORCED
    ))
    val historyFuture = UnindexedFilesScanner(project, false, false, null,
                                              false, trigger, false, scanningParameters = parameters).queue()
    try {
      return ProgressIndicatorUtils.awaitWithCheckCanceled(historyFuture).extractConsistencyProblems() +
             trigger.stubAndIndexingStampInconsistencies
    }
    catch (e: Exception) {
      return listOf(ExceptionalCompletionProblem(e))
    }
  }

  private fun ProjectScanningHistory.extractConsistencyProblems(): List<CacheInconsistencyProblem> =
    scanningStatistics.filter { it.numberOfFilesForIndexing != 0 }.map {
      UnindexedFilesInconsistencyProblem(it.numberOfFilesForIndexing, it.providerName)
    }

  private class UnindexedFilesInconsistencyProblem(private val numberOfFilesForIndexing: Int, private val providerName: String) : CacheInconsistencyProblem {
    override val message: String
      get() = "Provider `$providerName` had $numberOfFilesForIndexing unindexed files"
  }
}