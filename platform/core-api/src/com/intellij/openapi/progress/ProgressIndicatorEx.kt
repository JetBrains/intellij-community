// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress

inline fun <Y> ProgressIndicator.withPushPop(action: () -> Y): Y {
  pushState()
  try {
    return action()
  }
  finally {
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
      action(y, indicator)
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
      result += action(y, indicator)
    }
  }
  return result.toList()
}
