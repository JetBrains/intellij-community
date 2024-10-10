// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProgressIndicatorForCollections")
package com.intellij.openapi.progress

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.progress.util.RelayUiToDelegateIndicator
import org.jetbrains.annotations.ApiStatus

@Deprecated("Don't use this. Migrate to ProgressReporter")
@JvmSynthetic
inline fun <Y> ProgressIndicator.withPushPop(action: () -> Y): Y {
  val wasIndeterminate = isIndeterminate
  pushState()
  try {
    return action()
  }
  finally {
    isIndeterminate = wasIndeterminate
    popState()
  }
}

@Deprecated("Use `com.intellij.platform.util.progress.forEachWithProgress`")
@JvmSynthetic
inline fun <Y> Collection<Y>.forEachWithProgress(indicator: ProgressIndicator,
                                                 action: (Y, ProgressIndicator) -> Unit) {
  indicator.withPushPop {
    indicator.isIndeterminate = false
    indicator.checkCanceled()
    val size = this.size.toDouble()
    for ((i, y) in this.withIndex()) {
      indicator.checkCanceled()

      val lowerBound = i / size
      val upperBound = (i + 1) / size
      indicator.fraction = lowerBound

      val prevText = indicator.text
      val prevText2 = indicator.text2

      action(y, indicator.scaleFraction(lowerBound, upperBound))

      indicator.text = prevText
      indicator.text2 = prevText2
    }
    indicator.fraction = 1.0
  }
}

@Deprecated("Use `com.intellij.platform.util.progress.mapWithProgress`")
@JvmSynthetic
inline fun <Y, R> Collection<Y>.mapWithProgress(indicator: ProgressIndicator,
                                                action: (Y, ProgressIndicator) -> R): List<R> {
  indicator.checkCanceled()
  val result = mutableListOf<R>()
  forEachWithProgress(indicator) { y, it ->
    result += action(y, it)
  }
  indicator.checkCanceled()
  return result.toList()
}

@JvmSynthetic
@PublishedApi
@ApiStatus.Internal
internal fun ProgressIndicator.scaleFraction(
  lowerBound: Double,
  upperBound: Double
): ProgressIndicator {
  val parentProgress = this
  return object : SensitiveProgressWrapper(parentProgress) {
    private var myFraction = 0.0
    private val d = upperBound - lowerBound

    init {
      //necessary for push/pop state methods
      text = parentProgress.text
      text2 = parentProgress.text2
    }

    override fun getFraction() = synchronized(lock) { myFraction }

    override fun setFraction(fraction: Double) {
      //there is no need to propagate too small parts at all
      if (d <= 0.001) return

      synchronized(lock) {
        myFraction = fraction
      }

      parentProgress.fraction = (lowerBound + d * fraction).coerceIn(lowerBound, upperBound)
    }

    override fun setIndeterminate(indeterminate: Boolean) {
      //ignore
    }

    override fun isIndeterminate() = false
  }
}

/**
 * Implements the best effort in translating progress events from
 * the parent [parentProgress] into [childProgress] while running the
 * [action]. It may pass either of the progresses to the action
 */
@Deprecated("Migrate to `reportProgress`/`reportSequentialProgress`")
inline fun <Y> runUnderNestedProgressAndRelayMessages(parentProgress: ProgressIndicator,
                                                      childProgress: ProgressIndicator,
                                                      crossinline action: (ProgressIndicator) -> Y): Y {
  //avoid the action to be inlined multiple times in the code
  @Suppress("NAME_SHADOWING")
  val action : (ProgressIndicator) -> Y = { action(it) }

  //case 1 - parent progress is capable of delegates
  if (parentProgress is AbstractProgressIndicatorExBase) {
    return runWithStateDelegate(parentProgress, RelayUiToDelegateIndicator(childProgress))  {
      runUnderBoundCancellation(cancelOf = childProgress, cancels = parentProgress) {
        action(parentProgress)
      }
    }
  }

  //case 2 - we run it under child progress and delegate messages back
  if (childProgress is AbstractProgressIndicatorExBase) {
    return runWithStateDelegate(childProgress, RelayUiToDelegateIndicator(parentProgress)) {
      runUnderBoundCancellation(cancelOf = parentProgress, cancels = childProgress) {
        action(childProgress)
      }
    }
  }

  //case 3 - just run it under child progress, no connection is supported
  return action(childProgress)
}

/**
 * A best effort way to bind a cancellation of one progress with the other.
 */
@ApiStatus.Internal
@Deprecated("Use coroutines")
inline fun <Y> runUnderBoundCancellation(cancelOf: ProgressIndicator,
                                         cancels: ProgressIndicator,
                                         crossinline action: () -> Y) : Y {
  //avoid the action to be inlined multiple times in the code
  @Suppress("NAME_SHADOWING")
  val action : () -> Y = { action() }

  if (cancelOf !is AbstractProgressIndicatorExBase) {
    return action()
  }

  val relay = object: AbstractProgressIndicatorExBase() {
    override fun cancel() {
      super.cancel()
      cancels.cancel()
    }
  }

  return runWithStateDelegate(cancelOf, relay, action)
}

/**
 * Runs an action on [parentProgress] with the state delegate attached for the
 * time [action] runs.
 */
@PublishedApi
internal inline fun <Y> runWithStateDelegate(parentProgress: AbstractProgressIndicatorExBase,
                                    delegate: AbstractProgressIndicatorExBase,
                                    action: () -> Y): Y {
  parentProgress.addStateDelegate(delegate)
  try {
    return action()
  }
  finally {
    parentProgress.removeStateDelegate(delegate)
  }
}
