// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.checkNonNegative

/**
 * Defines top and bottom gaps. Values must be provided unscaled
 */
data class UnscaledGapsY(val top: Int = 0, val bottom: Int = 0) {
  companion object {
    @JvmField
    val EMPTY = UnscaledGapsY()
  }

  init {
    checkNonNegative("top", top)
    checkNonNegative("bottom", bottom)
  }

  val height: Int
    get() = top + bottom
}