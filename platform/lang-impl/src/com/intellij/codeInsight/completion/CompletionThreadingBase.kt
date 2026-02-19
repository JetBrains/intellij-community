// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class CompletionThreadingBase : CompletionThreading {
  protected abstract fun flushBatchResult(indicator: CompletionProgressIndicator)

  companion object {
    internal val isInBatchUpdate: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    @JvmStatic
    fun withBatchUpdate(runnable: Runnable, process: CompletionProcess?) {
      if (isInBatchUpdate.get() || process !is CompletionProgressIndicator) {
        runnable.run()
        return
      }

      try {
        isInBatchUpdate.set(true)
        runnable.run()
        ProgressManager.checkCanceled()
        val threading = process.completionThreading
        threading.flushBatchResult(process)
      }
      finally {
        isInBatchUpdate.set(false)
      }
    }
  }
}
