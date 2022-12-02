// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.google.common.util.concurrent.AtomicDouble
import com.intellij.openapi.progress.ProgressReporter
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
abstract class BaseProgressReporter(parentScope: CoroutineScope) : ProgressReporter {

  protected val cs: CoroutineScope = parentScope.childScope(supervisor = false)

  final override fun finish() {
    cs.cancel()
  }

  suspend fun awaitCompletion() {
    cs.coroutineContext.job.join()
  }

  /**
   * 0.0 -> initial
   * (0.0; 1.0] -> this reporter has determinate children
   */
  private val lastFraction = AtomicDouble(.0)

  private fun duration(endFraction: Double?): Double? {
    if (endFraction == null) {
      return null
    }
    require(.0 < endFraction && endFraction <= 1.0) {
      "End fraction must be in (0.0; 1.0], got: $endFraction"
    }
    val previousFraction = lastFraction.getAndUpdate {
      require(endFraction > it) {
        "New end fraction $endFraction must be greater than the previous end fraction $it"
      }
      endFraction
    }
    return endFraction - previousFraction
  }

  final override fun step(text: ProgressText?, endFraction: Double?): ProgressReporter {
    return createStep(duration(endFraction), text)
  }

  protected abstract fun createStep(duration: Double?, text: ProgressText?): ProgressReporter
}
