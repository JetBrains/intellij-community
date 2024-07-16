// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import com.intellij.diff.tools.combined.CombinedDiffRegistry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface CombinedDiffToggle {
  var isCombinedDiffEnabled: Boolean

  companion object {
    @JvmStatic
    val DEFAULT: CombinedDiffToggle = object : CombinedDiffToggle {
      override var isCombinedDiffEnabled: Boolean
        get() = CombinedDiffRegistry.isEnabled()
        set(value) {
          CombinedDiffRegistry.setCombinedDiffEnabled(value)
        }
    }
  }
}

