// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.checkNonNegative
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use UnscaledGapsY instead")
@ApiStatus.ScheduledForRemoval
data class VerticalGaps(val top: Int = 0, val bottom: Int = 0) {
  init {
    checkNonNegative("top", top)
    checkNonNegative("bottom", bottom)
  }
}
