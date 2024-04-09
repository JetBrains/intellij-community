// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.*
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
abstract class FilesScanningTaskBase(private val project: Project) : MergeableQueueTask<FilesScanningTask>, FilesScanningTask {
  override fun dispose() {}

  final override fun perform(indicator: ProgressIndicator) {
    val progressReporter = IndexingProgressReporter(indicator)
    val hideProgressInSmartMode = shouldHideProgressInSmartMode()
    val shouldShowProgress: StateFlow<Boolean> = if (hideProgressInSmartMode) {
      project.service<DumbModeWhileScanningTrigger>().isDumbModeForScanningActive()
    }
    else {
      MutableStateFlow(true)
    }

    val taskScope = CoroutineScope(Dispatchers.Default + Job())
    try {
      val pauseReason = UnindexedFilesScannerExecutor.getInstance(project).getPauseReason()
      val taskIndicator = IndexingProgressReporter.CheckCancelOnlyProgressIndicator(indicator, taskScope, pauseReason)
      launchIndexingProgressUIReporter(taskScope, project, shouldShowProgress, progressReporter,
                                       IndexingBundle.message("progress.indexing.scanning"),
                                       taskIndicator.getPauseReason())
      perform(taskIndicator, progressReporter)
    }
    finally {
      taskScope.cancel()
    }
  }

  protected open fun shouldHideProgressInSmartMode() = Registry.`is`("scanning.hide.progress.in.smart.mode", true)

  abstract fun perform(indicator: IndexingProgressReporter.CheckCancelOnlyProgressIndicator, progressReporter: IndexingProgressReporter)

  private fun launchIndexingProgressUIReporter(
    progressReportingScope: CoroutineScope,
    project: Project,
    shouldShowProgress: Flow<Boolean>,
    progressReporter: IndexingProgressReporter,
    progressTitle: @ProgressTitle String,
    pauseReason: Flow<@ProgressText String?>,
  ) {
    progressReportingScope.launch(DumbServiceGuiExecutor.IndexingType.SCANNING) {
      while (true) {
        shouldShowProgress.first { it }

        withBackgroundProgress(project, progressTitle, cancellable = false) {
          reportRawProgress { reporter ->
            async(Dispatchers.EDT) {
              pauseReason.collect { paused ->
                reporter.text(
                  if (paused != null) IdeBundle.message("dumb.service.indexing.paused.due.to", paused)
                  else progressTitle
                )
              }
            }
            async(Dispatchers.EDT) {
              progressReporter.subTaskTexts.collect {
                reporter.details(it.firstOrNull())
              }
            }
            async(Dispatchers.EDT) {
              progressReporter.subTasksFinished.collect {
                val subTasksCount = progressReporter.subTasksCount
                if (subTasksCount > 0) {
                  val newValue = (it.toDouble() / subTasksCount).coerceIn(0.0, 1.0)
                  reporter.fraction(newValue)
                }
                else {
                  reporter.fraction(0.0)
                }
              }
            }
            shouldShowProgress.first { !it }
            coroutineContext.cancelChildren() // cancel started async coroutines
          }
        }
      }
    }
  }

}