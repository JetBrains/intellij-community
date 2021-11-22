// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.ide.actions.cache.CacheInconsistencyProblem
import com.intellij.ide.actions.cache.ExceptionalCompletionProblem
import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.lang.LangBundle
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory
import org.jetbrains.annotations.Nls
import java.util.concurrent.CompletableFuture

internal class RescanIndexesAction : RecoveryAction {
  override val performanceRate: Int
    get() = 9990
  override val presentableName: @Nls(capitalization = Nls.Capitalization.Title) String
    get() = LangBundle.message("rescan.indexes.recovery.action.name")
  override val actionKey: String
    get() = "rescan"

  override fun performSync(project: Project): List<CacheInconsistencyProblem> {
    val historyFuture = CompletableFuture<ProjectIndexingHistory>()
    val updater = object : UnindexedFilesUpdater(project, "Rescanning indexes recovery action") {
      override fun performScanningAndIndexing(indicator: ProgressIndicator): ProjectIndexingHistory {
        try {
          val history = super.performScanningAndIndexing(indicator)
          historyFuture.complete(history)
          return history
        }
        catch (e: Exception) {
          historyFuture.completeExceptionally(e)
          throw e
        }
      }
    }
    DumbService.getInstance(project).queueTask(updater)
    try {
      return ProgressIndicatorUtils.awaitWithCheckCanceled(historyFuture).extractConsistencyProblems()
    }
    catch (e: Exception) {
      return listOf(ExceptionalCompletionProblem(e))
    }
  }

  private fun ProjectIndexingHistory.extractConsistencyProblems(): List<CacheInconsistencyProblem> =
    scanningStatistics.filter { it.numberOfFilesForIndexing != 0 }.map {
      UnindexedFilesInconsistencyProblem(it.numberOfFilesForIndexing, it.providerName)
    }

  private class UnindexedFilesInconsistencyProblem(private val numberOfFilesForIndexing: Int, private val providerName: String) : CacheInconsistencyProblem {
    override val message: String
      get() = "Provider `$providerName` had $numberOfFilesForIndexing unindexed files"
  }
}