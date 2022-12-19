// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.google.common.util.concurrent.AtomicDouble
import com.intellij.openapi.progress.ProgressReporter
import com.intellij.openapi.progress.RawProgressReporter
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
abstract class BaseProgressReporter(parentScope: CoroutineScope) : ProgressReporter {

  protected val cs: CoroutineScope = parentScope.childScope(supervisor = false)

  final override fun close() {
    cs.cancel()
  }

  suspend fun awaitCompletion() {
    cs.coroutineContext.job.join()
  }

  /**
   * (-♾️; 0.0) -> this reporter has indeterminate children
   * 0.0 -> initial
   * (0.0; 1.0] -> this reporter has determinate children
   * (1.0; +♾️) -> this reporter is raw
   */
  private val lastFraction = AtomicDouble(0.0)

  private fun duration(endFraction: Double): Double {
    require(0.0 < endFraction && endFraction <= 1.0) {
      "End fraction must be in (0.0; 1.0], got: $endFraction"
    }
    val previousFraction = lastFraction.getAndUpdate {
      when {
        it > 1.0 -> error("Cannot start a child because this reporter is raw.")
        endFraction <= it -> {
          throw IllegalArgumentException("New end fraction $endFraction must be greater than the previous end fraction $it")
        }
        else -> endFraction
      }
    }
    return endFraction - previousFraction.coerceAtLeast(0.0)
  }

  final override fun step(endFraction: Double, text: ProgressText?): ProgressReporter {
    return createStep(duration(endFraction), text)
  }

  final override fun durationStep(duration: Double, text: ProgressText?): ProgressReporter {
    require(duration in 0.0..1.0) {
      "Duration is expected to be a value in [0.0; 1.0], got $duration"
    }
    if (duration == 0.0) { // indeterminate
      lastFraction.getAndUpdate {
        when {
          it <= 0.0 -> it - 1.0 // indicate that this reporter has an indeterminate child, so rawReporter() would fail
          it <= 1.0 -> it // don't change
          else -> error("Cannot start an indeterminate child because this reporter is raw.")
        }
      }
      return createStep(duration = 0.0, text)
    }
    lastFraction.getAndUpdate {
      when {
        it > 1.0 -> error("Cannot start a child because this reporter is raw.")
        it <= 0.0 -> duration
        else -> {
          val newValue = it + duration
          check(0.0 < newValue && newValue <= 1.0)
          newValue
        }
      }
    }
    return createStep(duration, text)
  }

  protected abstract fun createStep(duration: Double, text: ProgressText?): ProgressReporter

  final override fun rawReporter(): RawProgressReporter {
    lastFraction.getAndUpdate {
      check(it == 0.0) {
        "This reporter already has child steps." +
        "Wrap the call into step(endFraction=...) and call rawReporter() inside the newly started child step."
      }
      2.0
    }
    return asRawReporter()
  }

  protected abstract fun asRawReporter(): RawProgressReporter
}
