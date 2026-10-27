// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.filters.gray
import com.intellij.platform.icons.impl.filters.GrayColorFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GrayColorFilterTest {
  @Test
  fun `gray filter keeps its values`() {
    testIcons {
      val filter = ColorFilter.gray(33, -35, 100)
      val gray = filter as GrayColorFilter

      assertEquals(33, gray.brightness)
      assertEquals(-35, gray.contrast)
      assertEquals(100, gray.alpha)
    }
  }

  @Test
  fun `values outside the ranges are clamped`() {
    testIcons {
      val high = ColorFilter.gray(300, -300, 500) as GrayColorFilter
      assertEquals(listOf(100, -100, 100), listOf(high.brightness, high.contrast, high.alpha))

      val low = ColorFilter.gray(-300, 300, -5) as GrayColorFilter
      assertEquals(listOf(-100, 100, 0), listOf(low.brightness, low.contrast, low.alpha))
    }
  }
}
