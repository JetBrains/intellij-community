// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.contentQueue

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.progress.SubTaskProgressIndicator
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicInteger

// TODO: merge with com.intellij.util.indexing.FilesScanningTaskBase.IndexingProgressReporter
@Internal
internal class IndexingProgressReporter2(private val indicator: ProgressIndicator) {
  private val totalFiles: AtomicInteger = AtomicInteger(0)
  private var processedFiles: AtomicInteger = AtomicInteger(0)

  fun setLocationBeingIndexed(originProgressName: @NlsContexts.ProgressText String?,
                              fileProgressName: @NlsContexts.ProgressText String) {
    if (indicator is SubTaskProgressIndicator) {
      indicator.setText(fileProgressName)
    }
    else {
      if (originProgressName != null && originProgressName != indicator.text) {
        indicator.text = originProgressName
      }
      indicator.text2 = fileProgressName
    }
  }

  fun setTotalFiles(totalFiles: Int) {
    logger<IndexingProgressReporter2>().assertTrue(this.totalFiles.get() == 0, "totalFiles already set")
    this.totalFiles.set(totalFiles)
  }

  fun oneMoreFileProcessed() {
    val newCount = processedFiles.incrementAndGet()
    val newFraction = newCount / totalFiles.get().coerceAtLeast(1).toDouble()
    try {
      indicator.fraction = newFraction
    }
    catch (ignored: Exception) {
      //Unexpected here. A misbehaved progress indicator must not break our code flow.
    }
  }
}