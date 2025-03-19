// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.checkNonNegative
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use UnscaledGaps instead")
@ApiStatus.ScheduledForRemoval
data class Gaps(val top: Int = 0, val left: Int = 0, val bottom: Int = 0, val right: Int = 0) {
  companion object {
    @Deprecated("Use UnscaledGaps instead", level = DeprecationLevel.HIDDEN)
    @ApiStatus.ScheduledForRemoval
    @JvmField
    val EMPTY: Gaps = Gaps(0, 0, 0, 0)
  }

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


@Deprecated("Use UnscaledGaps instead", replaceWith = ReplaceWith("UnscaledGaps(top, left, bottom, right)"), level = DeprecationLevel.HIDDEN)
@ApiStatus.ScheduledForRemoval
fun JBGaps(top: Int = 0, left: Int = 0, bottom: Int = 0, right: Int = 0): Gaps {
  return Gaps(JBUIScale.scale(top), JBUIScale.scale(left), JBUIScale.scale(bottom), JBUIScale.scale(right))
}

@ApiStatus.Internal
@Deprecated("Use UnscaledGaps", replaceWith = ReplaceWith("UnscaledGaps()"))
@ApiStatus.ScheduledForRemoval
fun Gaps.toUnscaled(): UnscaledGaps = UnscaledGaps(top = JBUI.unscale(top),
                                                   left = JBUI.unscale(left),
                                                   bottom = JBUI.unscale(bottom),
                                                   right = JBUI.unscale(right))
