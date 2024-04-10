// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.util.coroutines.flow.mapStateIn
import com.intellij.util.application
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.Consumer

// This class is thread safe
@Internal
class IndexingProgressReporter(private val indicator: ProgressIndicator) {
  @Volatile
  internal var subTasksCount: Int = 0
  internal val subTasksFinished = MutableStateFlow(0)
  internal val subTaskTexts = MutableStateFlow(persistentListOf<@NlsContexts.ProgressDetails String>())

  fun setSubTasksCount(value: Int) {
    thisLogger().assertTrue(subTasksCount == 0, "subTasksCount can be set only once. Previous value: $subTasksCount")
    indicator.isIndeterminate = false
    indicator.setFraction(0.0)
    subTasksCount = value
  }

  fun setIndeterminate(value: Boolean) {
    indicator.isIndeterminate = value
  }

  fun setText(value: @NlsContexts.ProgressText String) {
    indicator.text = value
  }

  fun getSubTaskReporter(): IndexingSubTaskProgressReporter {
    return IndexingSubTaskProgressReporter()
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
  class CheckPauseOnlyProgressIndicator(private val taskScope: CoroutineScope,
                                        private val pauseReason: StateFlow<PersistentList<@NlsContexts.ProgressText String>>) {
    private var paused = getPauseReason().mapStateIn(taskScope) { it != null }
    fun getPauseReason(): StateFlow<@NlsContexts.ProgressText String?> = pauseReason.mapStateIn(taskScope) { it.firstOrNull() }
    fun onPausedStateChanged(action: Consumer<Boolean>) {
      taskScope.launch {
        paused.collect {
          action.accept(it)
        }
      }
    }

    fun freezeIfPaused() {
      ProgressManager.checkCanceled()
      if (!paused.value) return
      if (application.isUnitTestMode) return // do not pause in unit tests, because some tests do not expect pausing
      if (application.isDispatchThread) {
        thisLogger().error("Ignore pause, because freezeIfPaused invoked on EDT")
      }
      else {
        runBlockingCancellable {
          coroutineScope {
            async(taskScope.coroutineContext) {
              // we don't expect that taskScope may cancel, because it will be canceled after the task has finished, but
              // the task will not be finished, because it is paused. This line here is just in case, if the logic changes in the future.
              while (true) {
                checkCanceled()
                delay(100) // will throw if taskScope has canceled
              }
            }
            async {
              while (true) {
                checkCanceled()
                delay(100) // will throw if progress indicator has canceled
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