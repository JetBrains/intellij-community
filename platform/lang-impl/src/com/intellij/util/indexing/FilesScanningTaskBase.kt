// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.FilesScanningTask
import com.intellij.openapi.project.MergeableQueueTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.flow.mapStateIn
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.util.application
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.Consumer

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
      val taskIndicator = CheckCancelOnlyProgressIndicator(indicator, taskScope, pauseReason)
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

  abstract fun perform(indicator: CheckCancelOnlyProgressIndicator, progressReporter: IndexingProgressReporter)

  private fun launchIndexingProgressUIReporter(
    progressReportingScope: CoroutineScope,
    project: Project,
    shouldShowProgress: Flow<Boolean>,
    progressReporter: IndexingProgressReporter,
    progressTitle: @ProgressTitle String,
    pauseReason: Flow<@ProgressText String?>,
  ) {
    progressReportingScope.launch {
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

  class CheckCancelOnlyProgressIndicator(private val original: ProgressIndicator,
                                         private val taskScope: CoroutineScope,
                                         private val pauseReason: StateFlow<PersistentList<@ProgressText String>>) {
    private var paused = getPauseReason().mapStateIn(taskScope) { it != null }
    fun getPauseReason(): StateFlow<@ProgressText String?> = pauseReason.mapStateIn(taskScope) { it.firstOrNull() }
    fun onPausedStateChanged(action: Consumer<Boolean>) {
      taskScope.launch {
        paused.collect {
          action.accept(it)
        }
      }
    }
    fun originalIndicatorOnlyToFlushIndexingQueueSynchronously(): ProgressIndicator = original
    fun isCanceled(): Boolean = original.isCanceled

    fun freezeIfPaused() {
      if (isCanceled()) {
        original.checkCanceled() // throw if canceled,
        return // or just return if inside non-cancellable section
      }
      if (!paused.value) return
      if (application.isUnitTestMode) return // do not pause in unit tests, because some tests do not expect pausing
      if (application.isDispatchThread) {
        thisLogger().error("Ignore pause, because freezeIfPaused invoked on EDT")
      }
      else {
        runBlockingCancellable {
          withContext(taskScope.coroutineContext) {
            coroutineScope {
              async {
                while (true) {
                  original.checkCanceled()
                  delay(100)
                }
              }
              paused.first { !it } // wait until paused==false, or taskScope is canceled, or progress indicator is canceled
              coroutineContext.cancelChildren()
            }
          }
        }
      }
    }
  }

  // This class is thread safe
  @Internal
  class IndexingProgressReporter(private val indicator: ProgressIndicator) {
    @Volatile
    internal var subTasksCount: Int = 0
    internal val subTasksFinished = MutableStateFlow(0)
    internal val subTaskTexts = MutableStateFlow(persistentListOf<@ProgressDetails String>())

    fun setSubTasksCount(value: Int) {
      thisLogger().assertTrue(subTasksCount == 0, "subTasksCount can be set only once. Previous value: $subTasksCount")
      indicator.isIndeterminate = false
      indicator.setFraction(0.0)
      subTasksCount = value
    }

    // This class is not thread safe
    @Internal
    inner class IndexingSubTaskProgressReporter : AutoCloseable {
      private var oldText: @ProgressDetails String? = null
      fun setText(value: @ProgressDetails String) {
        // First add, then remove. To avoid blinking if old text is the only text in the list
        // We insert to the beginning to make sure that the text is rendered immediately. Otherwise, there will be an impression that
        // indexing is slow, if the text does not change often enough.
        subTaskTexts.update { it.add(0, value) }
        if (oldText != null) {
          subTaskTexts.update { it.remove(oldText!! /* this class should be used from single thread */) }
        }
        oldText = value
      }

      override fun close() {
        subTasksFinished.update { it + 1 }
        if (oldText != null) {
          subTaskTexts.update { it.remove(oldText!! /* this class should be used from single thread */) }
          oldText = null
        }
      }
    }

    fun setIndeterminate(value: Boolean) {
      indicator.isIndeterminate = value
    }

    fun setText(value: @ProgressText String) {
      indicator.text = value
    }

    fun getSubTaskReporter(): IndexingSubTaskProgressReporter {
      return IndexingSubTaskProgressReporter()
    }
  }
}