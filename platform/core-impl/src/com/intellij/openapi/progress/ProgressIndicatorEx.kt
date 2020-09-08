// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ProgressIndicatorForCollections")
package com.intellij.openapi.progress

import com.intellij.concurrency.SensitiveProgressWrapper
import org.jetbrains.annotations.ApiStatus

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
