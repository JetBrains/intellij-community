// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.checkNonNegative
import com.intellij.ui.scale.JBUIScale

data class VerticalGaps(val top: Int = 0, val bottom: Int = 0) {
  companion object {
    @JvmField
    val EMPTY = VerticalGaps()
  }

  init {
    checkNonNegative("top", top)
    checkNonNegative("bottom", bottom)
  }

  val height: Int
    get() = top + bottom
}

fun JBVerticalGaps(top: Int = 0, bottom: Int = 0): VerticalGaps {
  return VerticalGaps(JBUIScale.scale(top), JBUIScale.scale(bottom))
}