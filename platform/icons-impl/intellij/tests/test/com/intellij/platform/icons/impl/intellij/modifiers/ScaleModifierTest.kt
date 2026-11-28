// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.modifiers

import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.impl.intellij.testIcons
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.scale.fitArea
import org.junit.jupiter.api.Test

class ScaleModifierTest {
  @Test
  fun `should properly apply scale modifier with fitArea scale`() {
    testIcons {
      val imgA = testImage(20, 20)

      val result = pretendToRender(
        iconDescriptor {
          row {
            image(imgA, modifier = IconModifier.scale(fitArea(25.dp, 25.dp)))
          }
        }
      )

      result.assertSize(25,25)
      result.assertImage(0, 0, 25, 25, imgA)
    }
  }

  @Test
  fun `should properly apply scale modifier with factor scale`() {
    testIcons {
      val imgA = testImage(20, 20)

      val result = pretendToRender(
        iconDescriptor {
          row {
            image(imgA, modifier = IconModifier.scale(factor(2.0f)))
          }
        }
      )

      result.assertSize(40,40)
      result.assertImage(0, 0, 40, 40, imgA)
    }
  }

  @Test
  fun `a fitArea that may not upscale keeps smaller content at its size and shrinks larger content`() {
    testIcons {
      val small = testImage(16, 16)
      val large = testImage(36, 36)

      val smallResult = pretendToRender(
        iconDescriptor { image(small, modifier = IconModifier.scale(fitArea(18.dp, 18.dp, upscale = false))) }
      )
      smallResult.assertSize(16, 16)
      smallResult.assertImage(0, 0, 16, 16, small)

      val largeResult = pretendToRender(
        iconDescriptor { image(large, modifier = IconModifier.scale(fitArea(18.dp, 18.dp, upscale = false))) }
      )
      largeResult.assertSize(18, 18)
      largeResult.assertImage(0, 0, 18, 18, large)
    }
  }

  @Test
  fun `a fitArea that may not downscale enlarges smaller content and keeps larger content at its size`() {
    testIcons {
      val small = testImage(9, 9)
      val large = testImage(36, 36)

      val smallResult = pretendToRender(
        iconDescriptor { image(small, modifier = IconModifier.scale(fitArea(18.dp, 18.dp, downscale = false))) }
      )
      smallResult.assertSize(18, 18)

      val largeResult = pretendToRender(
        iconDescriptor { image(large, modifier = IconModifier.scale(fitArea(18.dp, 18.dp, downscale = false))) }
      )
      largeResult.assertSize(36, 36)
    }
  }

  @Test
  fun `a fitArea that may not upscale fits the content also inside a nested icon`() {
    testIcons {
      val large = testImage(36, 36)
      val fitted = iconDescriptor { image(large, modifier = IconModifier.scale(fitArea(18.dp, 18.dp, upscale = false))) }

      // e.g. the disabled variant of an action icon, which wraps the icon in a nested layer
      val result = pretendToRender(iconDescriptor { icon(fitted) })

      result.assertSize(18, 18)
      result.assertImage(0, 0, 18, 18, large)
    }
  }

  @Test
  fun `a fitArea that may not upscale fits the content also inside a row`() {
    testIcons {
      val large = testImage(36, 36)
      val small = testImage(16, 16)

      val result = pretendToRender(
        iconDescriptor {
          row {
            image(large, modifier = IconModifier.scale(fitArea(18.dp, 18.dp, upscale = false)))
            image(small)
          }
        }
      )

      result.assertSize(18 + 16, 18)
    }
  }
}
