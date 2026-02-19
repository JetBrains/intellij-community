// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbServiceGuiExecutor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.flow.mapStateIn
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.Consumer

// This class is thread safe
internal class IndexingProgressReporter {
  @Volatile
  internal var subTasksCount: Int = 0
  internal val operationName = MutableStateFlow<@ProgressText String?>(null)
  internal val subTasksFinished = MutableStateFlow(0)
  internal val subTaskTexts = MutableStateFlow(persistentListOf<@NlsContexts.ProgressDetails String>())

  fun setSubTasksCount(value: Int) {
    thisLogger().assertTrue(subTasksCount == 0, "subTasksCount can be set only once. Previous value: $subTasksCount")
    subTasksCount = value
  }

  fun setText(value: @ProgressText String) {
    operationName.value = value
  }

  fun getSubTaskReporter(): IndexingSubTaskProgressReporter {
    return IndexingSubTaskProgressReporter()
  }

  companion object {
    fun launchIndexingProgressUIReporter(
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

          withBackgroundProgress(project, progressTitle,
                                 cancellation = TaskCancellation.nonCancellable(),
                                 suspender = null,
                                 visibleInStatusBar = false) {
            reportRawProgress { reporter ->
              async {
                pauseReason
                  .combine(progressReporter.operationName) { paused, operation ->
                    if (paused != null) IdeBundle.message("dumb.service.indexing.paused.due.to", paused)
                    else operation ?: progressTitle
                  }
                  .collect(reporter::text)
              }
              async {
                progressReporter.subTaskTexts.collect {
                  reporter.details(it.firstOrNull())
                }
              }
              async {
                progressReporter.subTasksFinished.collect {
                  val subTasksCount = progressReporter.subTasksCount
                  if (subTasksCount > 0) {
                    val newValue = (it.toDouble() / subTasksCount).coerceIn(0.0, 1.0)
                    reporter.fraction(newValue)
                  }
                  else {
                    reporter.fraction(null)
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

  // This class is not thread safe
  @Internal
  inner class IndexingSubTaskProgressReporter : AutoCloseable {
    private var oldText: @NlsContexts.ProgressDetails String? = null
    fun setText(value: @NlsContexts.ProgressDetails String) {
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

  @Internal
  interface CheckPauseOnlyProgressIndicator {
    fun isPaused(): Boolean
    suspend fun suspendIfPaused()
    fun onPausedStateChanged(action: Consumer<Boolean>)
  }

  @Internal
  internal class CheckPauseOnlyProgressIndicatorImpl(private val taskScope: CoroutineScope,
                                                     private val pauseReason: StateFlow<PersistentList<@ProgressText String>>
  ) : CheckPauseOnlyProgressIndicator {
    private var paused = getPauseReason().mapStateIn(taskScope) { it != null }
    internal fun getPauseReason(): StateFlow<@ProgressText String?> = pauseReason.mapStateIn(taskScope) { it.firstOrNull() }

    override fun onPausedStateChanged(action: Consumer<Boolean>) {
      taskScope.launch {
        paused.collect {
          action.accept(it)
        }
      }
    }

    override fun isPaused(): Boolean {
      return pauseReason.value.isNotEmpty()
    }

    override suspend fun suspendIfPaused() {
      pauseReason.first { it.isEmpty() }
    }
  }
}