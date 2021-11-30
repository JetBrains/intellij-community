// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.impl

import com.intellij.navigation.TargetPresentation
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * "Go To Declaration" and "Go To Type Declaration" action result
 */
@Internal
sealed class NavigationActionResult {

  /**
   * Single [Navigatable].
   *
   * Might be obtained from direct navigation, in this case requiring [TargetPresentation] doesn't make sense.
   */
  class SingleTarget internal constructor(val navigatable: Navigatable, val navigationProvider: Any?) : NavigationActionResult()

  class MultipleTargets internal constructor(val targets: List<LazyTargetWithPresentation>) : NavigationActionResult() {
    init {
      require(targets.isNotEmpty())
    }
  }
}

@Internal
data class LazyTargetWithPresentation internal constructor(
  val navigatable: () -> Navigatable?,
  val presentation: TargetPresentation,
  val navigationProvider: Any?,
)
