// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.project.FilesScanningTask
import com.intellij.openapi.project.MergeableQueueTask
import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.util.NlsContexts.ProgressText
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

@Internal
abstract class FilesScanningTaskBase : MergeableQueueTask<FilesScanningTask>, FilesScanningTask {
  final override fun perform(indicator: ProgressIndicator) {
    perform(CheckCancelOnlyProgressIndicator(indicator), IndexingProgressReporter(indicator))
  }

  abstract fun perform(indicator: CheckCancelOnlyProgressIndicator, progressReporter: IndexingProgressReporter)

  class CheckCancelOnlyProgressIndicator(private val original: ProgressIndicator) {
    fun originalIndicatorOnlyToFlushIndexingQueueSynchronously(): ProgressIndicator = original
    fun isCanceled(): Boolean = original.isCanceled
    fun getSuspender(): ProgressSuspender? = ProgressSuspender.getSuspender(original)
  }

  // This class is thread safe
  @Internal
  class IndexingProgressReporter(private val indicator: ProgressIndicator) {
    private var subTasksCount: Int = 0
    private val subTasksFinished = AtomicInteger()
    private val subTaskTexts = ConcurrentLinkedDeque<@ProgressDetails String>()

    fun setSubTasksCount(value: Int) {
      thisLogger().assertTrue(subTasksCount == 0, "subTasksCount can be set only once. Previous value: $subTasksCount")
      indicator.isIndeterminate = false
      indicator.setFraction(0.0)
      subTasksCount = value
    }

    private fun refreshProgressTextFromSubTask() {
      // there is no race: item is added/deleted, and only then refreshProgressTextFromSubTask is invoked.
      // if refreshProgressTextFromSubTask sees not up-to-date state, this means that there will be one more refreshProgressTextFromSubTask
      // (i.e. indicator text is eventually consistent)
      indicator.text2 = subTaskTexts.peek()
    }

    // This class is not thread safe
    @Internal
    inner class IndexingSubTaskProgressReporter : AutoCloseable {
      private var oldText: @ProgressDetails String? = null
      fun setText(value: @ProgressDetails String) {
        // First add, then remove. To avoid blinking if old text is the only text in the list
        // We insert to the beginning to make sure that the text is rendered immediately. Otherwise, there will be an impression that
        // indexing is slow, if the text does not change often enough.
        subTaskTexts.addFirst(value)
        if (oldText != null) {
          subTaskTexts.remove(oldText)
        }
        oldText = value
        refreshProgressTextFromSubTask()
      }

      override fun close() {
        val newVal = subTasksFinished.incrementAndGet()
        indicator.fraction = (newVal.toDouble() / subTasksCount).coerceAtMost(1.0)
        if (oldText != null) {
          subTaskTexts.remove(oldText)
          oldText = null
        }
        refreshProgressTextFromSubTask()
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