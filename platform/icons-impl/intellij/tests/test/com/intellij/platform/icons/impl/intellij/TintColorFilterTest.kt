// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.design.BlendMode
import com.intellij.platform.icons.impl.design.DefaultSRGB
import com.intellij.platform.icons.impl.filters.GrayColorFilter
import com.intellij.platform.icons.impl.filters.TintColorFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TintColorFilterTest {
  private val red = DefaultSRGB(1f, 0f, 0f, 1f)

  @Test
  fun `tint filters with the same color and blend mode are equal`() {
    val filter = TintColorFilter(red, BlendMode.Multiply)
    val same = TintColorFilter(DefaultSRGB(1f, 0f, 0f, 1f), BlendMode.Multiply)

    assertEquals(filter, same)
    assertEquals(filter.hashCode(), same.hashCode())
  }

  @Test
  fun `tint filters with a different color or blend mode are not equal`() {
    val filter = TintColorFilter(red, BlendMode.Multiply)

    assertNotEquals(filter, TintColorFilter(DefaultSRGB(0f, 1f, 0f, 1f), BlendMode.Multiply))
    assertNotEquals(filter, TintColorFilter(red, BlendMode.SrcIn))
  }

  @Test
  fun `tint filter is not equal to a gray filter`() {
    assertNotEquals(TintColorFilter(red), GrayColorFilter(0, 0, 100))
  }

  @Test
  fun `tint filter names its color and blend mode in toString`() {
    val text = TintColorFilter(red, BlendMode.Multiply).toString()

    assertTrue(text.startsWith("TintColorFilter(color="), text)
    assertTrue(text.endsWith("blendMode=Multiply)"), text)
  }
}
