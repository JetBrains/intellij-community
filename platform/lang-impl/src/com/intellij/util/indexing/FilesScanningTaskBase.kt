// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.progress.rawProgressReporter
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.progress.withRawProgressReporter
import com.intellij.openapi.project.FilesScanningTask
import com.intellij.openapi.project.MergeableQueueTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
abstract class FilesScanningTaskBase(private val project: Project) : MergeableQueueTask<FilesScanningTask>, FilesScanningTask {
  override fun dispose() {}

  final override fun perform(indicator: ProgressIndicator) {
    val progressReporter = IndexingProgressReporter(indicator)
    val shouldShowProgress: StateFlow<Boolean> = MutableStateFlow(true) // TODO

    IndexingProgressUIReporter(project, shouldShowProgress, progressReporter, IndexingBundle.message("progress.indexing.scanning")).use {
      perform(CheckCancelOnlyProgressIndicator(indicator), progressReporter)
    }
  }

  abstract fun perform(indicator: CheckCancelOnlyProgressIndicator, progressReporter: IndexingProgressReporter)

  private class IndexingProgressUIReporter(
    project: Project,
    shouldShowProgress: Flow<Boolean>,
    progressReporter: IndexingProgressReporter,
    progressTitle: @ProgressTitle String,
  ) : AutoCloseable {
    private val progressReportingScope = CoroutineScope(Dispatchers.Default + Job())

    init {
      progressReportingScope.launch {
        while (true) {
          shouldShowProgress.first { it }

          withBackgroundProgress(project, progressTitle, cancellable = false) {
            withRawProgressReporter {
              coroutineScope {
                async(Dispatchers.EDT) {
                  progressReporter.subTaskTexts.collect {
                    rawProgressReporter!!.details(it.firstOrNull())
                  }
                }
                async(Dispatchers.EDT) {
                  progressReporter.subTasksFinished.collect {
                    val subTasksCount = progressReporter.subTasksCount
                    if (subTasksCount > 0) {
                      val newValue = (it.toDouble() / subTasksCount).coerceIn(0.0, 1.0)
                      rawProgressReporter!!.fraction(newValue)
                    }
                    else {
                      rawProgressReporter!!.fraction(0.0)
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

    override fun close() {
      progressReportingScope.cancel()
    }
  }

  class CheckCancelOnlyProgressIndicator(private val original: ProgressIndicator) {
    fun originalIndicatorOnlyToFlushIndexingQueueSynchronously(): ProgressIndicator = original
    fun isCanceled(): Boolean = original.isCanceled
    fun getSuspender(): ProgressSuspender? = ProgressSuspender.getSuspender(original)
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