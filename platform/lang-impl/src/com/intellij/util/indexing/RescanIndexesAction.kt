// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.ide.actions.cache.*
import com.intellij.lang.LangBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.stubs.StubTreeBuilder
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.util.BooleanFunction
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.ProjectIndexableFilesIteratorImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.*
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
class RescanIndexesAction : RecoveryAction {
  override val performanceRate: Int
    get() = 9990
  override val presentableName: @Nls(capitalization = Nls.Capitalization.Title) String
    get() = LangBundle.message("rescan.indexes.recovery.action.name")
  override val actionKey: String
    get() = "rescan"

  override fun performSync(recoveryScope: RecoveryScope): List<CacheInconsistencyProblem> {
    val project = recoveryScope.project
    val historyFuture = CompletableFuture<ProjectIndexingHistoryImpl>()
    val stubAndIndexingStampInconsistencies = Collections.synchronizedList(arrayListOf<CacheInconsistencyProblem>())

    var predefinedIndexableFilesIterators: List<IndexableFilesIterator>? = null
    if (recoveryScope is FilesRecoveryScope) {
      predefinedIndexableFilesIterators = recoveryScope.files.map { ProjectIndexableFilesIteratorImpl(it) }
    }
    object : UnindexedFilesUpdater(project, predefinedIndexableFilesIterators, "Rescanning indexes recovery action") {
      private val stubIndex =
        runCatching { (FileBasedIndex.getInstance() as FileBasedIndexImpl).getIndex(StubUpdatingIndex.INDEX_ID) }
        .onFailure { logger<RescanIndexesAction>().error(it) }.getOrNull()

      private inner class StubAndIndexStampInconsistency(private val path: String): CacheInconsistencyProblem {
        override val message: String
          get() = "`$path` should have already indexed stub but it's not present"
      }

      override fun getForceReindexingTrigger(): BooleanFunction<IndexedFile>? {
        if (stubIndex != null) {
          return BooleanFunction<IndexedFile> {
            val fileId = (it.file as VirtualFileWithId).id
            if (stubIndex.getIndexingStateForFile(fileId, it) == FileIndexingState.UP_TO_DATE &&
                stubIndex.getIndexedFileData(fileId).isEmpty() &&
                isAbleToBuildStub(it.file)) {
              stubAndIndexingStampInconsistencies.add(StubAndIndexStampInconsistency(it.file.path))
              return@BooleanFunction true
            }
            false
          }
        }
        return null
      }

      private fun isAbleToBuildStub(file: VirtualFile): Boolean = runCatching {
        StubTreeBuilder.buildStubTree(FileContentImpl.createByFile(file))
      }.getOrNull() != null

      override fun performScanningAndIndexing(indicator: ProgressIndicator): ProjectIndexingHistoryImpl {
        try {
          IndexingFlag.cleanupProcessedFlag()
          val history = super.performScanningAndIndexing(indicator)
          historyFuture.complete(history)
          return history
        }
        catch (e: Exception) {
          historyFuture.completeExceptionally(e)
          throw e
        }
      }

      override fun tryMergeWith(taskFromQueue: DumbModeTask): DumbModeTask? =
        if (taskFromQueue is UnindexedFilesUpdater && project == taskFromQueue.myProject && taskFromQueue.javaClass == javaClass) this else null
    }.queue(project)
    try {
      return ProgressIndicatorUtils.awaitWithCheckCanceled(historyFuture).extractConsistencyProblems() +
             stubAndIndexingStampInconsistencies
    }
    catch (e: Exception) {
      return listOf(ExceptionalCompletionProblem(e))
    }
  }

  private fun ProjectIndexingHistoryImpl.extractConsistencyProblems(): List<CacheInconsistencyProblem> =
    scanningStatistics.filter { it.numberOfFilesForIndexing != 0 }.map {
      UnindexedFilesInconsistencyProblem(it.numberOfFilesForIndexing, it.providerName)
    }

  private class UnindexedFilesInconsistencyProblem(private val numberOfFilesForIndexing: Int, private val providerName: String) : CacheInconsistencyProblem {
    override val message: String
      get() = "Provider `$providerName` had $numberOfFilesForIndexing unindexed files"
  }
}