// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.impl

import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
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
  class SingleTarget internal constructor(val requestor: NavigationRequestor, val navigationProvider: Any?) : NavigationActionResult()

  class MultipleTargets internal constructor(val targets: List<LazyTargetWithPresentation>) : NavigationActionResult() {
    init {
      require(targets.isNotEmpty())
    }
  }
}

@Internal
data class LazyTargetWithPresentation internal constructor(
  val requestor: NavigationRequestor,
  val presentation: TargetPresentation,
  val navigationProvider: Any?,
)

/**
 * A [java.util.function.Supplier] of [NavigationRequest], but with annotations.
 */
@Internal
fun interface NavigationRequestor {

  @RequiresReadLock
  @RequiresBackgroundThread
  fun navigationRequest(): NavigationRequest?
}
