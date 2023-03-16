// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.checkNonNegative
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Insets
import kotlin.math.roundToInt

data class UnscaledGaps(val top: Int = 0, val left: Int = 0, val bottom: Int = 0, val right: Int = 0) {
  companion object {
    @JvmField
    val EMPTY = UnscaledGaps(0)
  }

  init {
    checkNonNegative("top", top)
    checkNonNegative("left", left)
    checkNonNegative("bottom", bottom)
    checkNonNegative("right", right)
  }

  constructor(size: Int) : this(size, size, size, size)

  val width: Int
    get() = left + right

  val height: Int
    get() = top + bottom
}

fun Insets.toUnscaledGaps(): UnscaledGaps =
  if (this is JBInsets) unscaled.let { insets ->
    UnscaledGaps(top = insets.top, left = insets.left, bottom = insets.bottom, right = insets.right)
  }
  else toGaps().toUnscaled()

@Internal
fun Int.unscale(): Int = (this / JBUIScale.scale(1f)).roundToInt()

fun UnscaledGaps.toJBEmptyBorder(): JBEmptyBorder {
  return JBEmptyBorder(top, left, bottom, right)
}
