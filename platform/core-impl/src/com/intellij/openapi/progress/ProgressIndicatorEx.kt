// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress

import com.intellij.concurrency.SensitiveProgressWrapper
import org.jetbrains.annotations.ApiStatus

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

inline fun <Y> Collection<Y>.forEachWithProgress(indicator: ProgressIndicator,
                                                 action: (Y, ProgressIndicator) -> Unit) = indicator.withPushPop {
  indicator.isIndeterminate = false
  val size = this.size.toDouble()
  for ((i, y) in this.withIndex()) {
    indicator.fraction = i / size
    indicator.withPushPop {
      action(y, indicator.ignoreFraction())
    }
  }
}

inline fun <Y, R> Collection<Y>.mapWithProgress(indicator: ProgressIndicator,
                                                action: (Y, ProgressIndicator) -> R): List<R> = indicator.withPushPop {
  indicator.isIndeterminate = false
  val size = this.size.toDouble()
  val result = mutableListOf<R>()
  for ((i, y) in this.withIndex()) {
    indicator.fraction = i / size
    indicator.withPushPop {
      result += action(y, indicator.ignoreFraction())
    }
  }
  return result.toList()
}

@PublishedApi
@ApiStatus.Internal
internal fun ProgressIndicator.ignoreFraction() : ProgressIndicator {
  val parentProgress = this
  return object: SensitiveProgressWrapper(parentProgress) {
    init {
      //necessary for push/pop state methods
      text = parentProgress.text
      text2 = parentProgress.text2
    }

    override fun setFraction(fraction: Double) {
      //ignore
    }

    override fun setIndeterminate(indeterminate: Boolean) {
      //ignore
    }

    override fun isIndeterminate(): Boolean {
      return true
    }
  }
}
