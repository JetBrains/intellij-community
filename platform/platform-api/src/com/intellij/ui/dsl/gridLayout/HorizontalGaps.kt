// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.checkNonNegative

@Deprecated("Use UnscaledGapsX instead")
data class HorizontalGaps(val left: Int = 0, val right: Int = 0) {
  init {
    checkNonNegative("left", left)
    checkNonNegative("right", right)
  }
}
