// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.filters.gray
import com.intellij.platform.icons.impl.filters.GrayColorFilter
import com.intellij.platform.icons.impl.modifiers.ColorFilterModifier
import com.intellij.platform.icons.impl.modifiers.CombinedIconModifier
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.grayFilter
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.assertThrows
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
  fun `gray filter without arguments has no effect`() {
    testIcons {
      val gray = ColorFilter.gray() as GrayColorFilter

      assertEquals(listOf(0, 0, 100), listOf(gray.brightness, gray.contrast, gray.alpha))
    }
  }

  @Test
  fun `gray filter modifier without arguments has no effect`() {
    testIcons {
      val gray = grayFilterOf(IconModifier.grayFilter())

      assertEquals(listOf(0, 0, 100), listOf(gray.brightness, gray.contrast, gray.alpha))
    }
  }

  @Test
  fun `gray filter modifier passes its values to the filter`() {
    testIcons {
      val gray = grayFilterOf(IconModifier.grayFilter(33, -35, 50))

      assertEquals(listOf(33, -35, 50), listOf(gray.brightness, gray.contrast, gray.alpha))
    }
  }

  @Test
  fun `gray filter rejects a value outside the ranges`() {
    testIcons {
      assertThrows<IllegalArgumentException> { ColorFilter.gray(brightness = 101) }
      assertThrows<IllegalArgumentException> { ColorFilter.gray(contrast = -101) }
      assertThrows<IllegalArgumentException> { ColorFilter.gray(alpha = -5) }
    }
  }

  @Test
  fun `gray filters with the same values are equal`() {
    val filter = GrayColorFilter(33, -35, 100)
    val same = GrayColorFilter(33, -35, 100)

    assertEquals(filter, same)
    assertEquals(filter.hashCode(), same.hashCode())
  }

  @Test
  fun `gray filters with different values are not equal`() {
    val filter = GrayColorFilter(33, -35, 100)

    assertNotEquals(filter, GrayColorFilter(34, -35, 100))
    assertNotEquals(filter, GrayColorFilter(33, -34, 100))
    assertNotEquals(filter, GrayColorFilter(33, -35, 99))
  }

  @Test
  fun `gray filter names its values in toString`() {
    assertEquals("GrayColorFilter(brightness=33, contrast=-35, alpha=100)", GrayColorFilter(33, -35, 100).toString())
  }

  @Test
  fun `gray filter modifiers with the same values are equal`() {
    testIcons {
      val modifier = IconModifier.grayFilter(33, -35, 100)
      val same = IconModifier.grayFilter(33, -35, 100)

      assertEquals(modifier, same)
      assertEquals(modifier.hashCode(), same.hashCode())
      assertNotEquals(modifier, IconModifier.grayFilter(33, -35, 50))
    }
  }

  @Test
  fun `constructor rejects a value outside the ranges`() {
    for ((brightness, contrast, alpha) in
      listOf(
        Triple(101, 0, 100),
        Triple(-101, 0, 100),
        Triple(0, 101, 100),
        Triple(0, -101, 100),
        Triple(0, 0, 101),
        Triple(0, 0, -1),
      )) {
      assertThrows<IllegalArgumentException>("$brightness, $contrast, $alpha") {
        GrayColorFilter(brightness, contrast, alpha)
      }
    }
  }

  @Test
  fun `constructor accepts the ends of the ranges`() {
    GrayColorFilter(-100, -100, 0)
    GrayColorFilter(100, 100, 100)
  }

  @Test
  fun `deserialization rejects a value outside the ranges`() {
    val json = Json.encodeToString(GrayColorFilter.serializer(), GrayColorFilter(33, -35, 100))
    assertEquals(GrayColorFilter(33, -35, 100), Json.decodeFromString(GrayColorFilter.serializer(), json))

    assertThrows<IllegalArgumentException> {
      Json.decodeFromString(GrayColorFilter.serializer(), """{"brightness":101,"contrast":0,"alpha":100}""")
    }
  }

  private fun grayFilterOf(modifier: IconModifier): GrayColorFilter =
    ((modifier as CombinedIconModifier).other as ColorFilterModifier).colorFilter as GrayColorFilter
}
