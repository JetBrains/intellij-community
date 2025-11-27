// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.checkNonNegative
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use UnscaledGaps instead")
@ApiStatus.ScheduledForRemoval
data class Gaps(val top: Int = 0, val left: Int = 0, val bottom: Int = 0, val right: Int = 0) {

  init {
    checkNonNegative("top", top)
    checkNonNegative("left", left)
    checkNonNegative("bottom", bottom)
    checkNonNegative("right", right)
  }

  @get:ApiStatus.ScheduledForRemoval
  @get:ApiStatus.Internal
  @get:Deprecated("Use UnscaledGaps instead")
  val width: Int
    get() = left + right

  @get:ApiStatus.ScheduledForRemoval
  @get:ApiStatus.Internal
  @get:Deprecated("Use UnscaledGaps instead")
  val height: Int
    get() = top + bottom
}


@ApiStatus.Internal
@Deprecated("Use UnscaledGaps", replaceWith = ReplaceWith("UnscaledGaps()"))
@ApiStatus.ScheduledForRemoval
fun Gaps.toUnscaled(): UnscaledGaps = UnscaledGaps(top = JBUI.unscale(top),
                                                   left = JBUI.unscale(left),
                                                   bottom = JBUI.unscale(bottom),
                                                   right = JBUI.unscale(right))
