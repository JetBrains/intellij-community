// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.design.sRGB
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ColorTest {
  @Test
  fun `should properly parse hex color`() {
    testIcons {
      val color = sRGB("#FF0000FF")
      assert(color.red == 1f)
      assert(color.green == 0f)
      assert(color.blue == 0f)
      assert(color.alpha == 1f)
    }
  }

  @Test
  fun `should properly parse hex color without alpha`() {
    testIcons {
      val color = sRGB("#FF0000")
      assert(color.red == 1f)
      assert(color.green == 0f)
      assert(color.blue == 0f)
      assert(color.alpha == 1f)
    }
  }


  @Test
  fun `should throw exception if color is invalid`() {
    testIcons {
      assertThrows<IllegalArgumentException> {
        sRGB("TOTALLY NOT VALID COLOR")
      }
    }
  }
}
