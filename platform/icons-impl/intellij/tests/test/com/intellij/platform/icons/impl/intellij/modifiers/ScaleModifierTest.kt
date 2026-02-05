// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.modifiers

import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.icon
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
        icon {
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
        icon {
          row {
            image(imgA, modifier = IconModifier.scale(factor(2.0f)))
          }
        }
      )

      result.assertSize(40,40)
      result.assertImage(0, 0, 40, 40, imgA)
    }
  }
}
