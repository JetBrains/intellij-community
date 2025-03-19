// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.contentQueue

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.progress.SubTaskProgressIndicator
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicInteger

// TODO: merge with com.intellij.util.indexing.FilesScanningTaskBase.IndexingProgressReporter
@Internal
interface IndexingProgressReporter2 {
  fun setLocationBeingIndexed(fileProgressName: @NlsContexts.ProgressText String)
  fun filesProcessed(filesProcessedCount: Int)


  companion object {
    fun createInstance(indicator: ProgressIndicator, totalFiles: Int): IndexingProgressReporter2 {
      return IndexingProgressReporter2OverProgressIndicator(indicator, totalFiles)
    }

    @TestOnly
    fun createEmpty(): IndexingProgressReporter2 {
      return IndexingProgressReporter2Empty()
    }
  }
}

private class IndexingProgressReporter2Empty : IndexingProgressReporter2 {
  override fun setLocationBeingIndexed(fileProgressName: @NlsContexts.ProgressText String) = Unit
  override fun filesProcessed(filesProcessedCount: Int) = Unit
}

private class IndexingProgressReporter2OverProgressIndicator(
  private val indicator: ProgressIndicator,
  totalFiles: Int,
) : IndexingProgressReporter2 {
  private val totalFiles: AtomicInteger = AtomicInteger(totalFiles)
  private var processedFiles: AtomicInteger = AtomicInteger(0)

  override fun setLocationBeingIndexed(fileProgressName: @NlsContexts.ProgressText String) {
    if (indicator is SubTaskProgressIndicator) {
      indicator.text = fileProgressName
    }
    else {
      indicator.text2 = fileProgressName
    }
  }

  override fun filesProcessed(filesProcessedCount: Int) {
    val newCount = processedFiles.addAndGet(filesProcessedCount)
    val newFraction = newCount / totalFiles.get().coerceAtLeast(1).toDouble()
    try {
      indicator.fraction = newFraction
    }
    catch (ignored: Exception) {
      //Unexpected here. A misbehaved progress indicator must not break our code flow.
    }
  }
}