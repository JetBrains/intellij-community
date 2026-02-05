// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.modifiers

import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.icon
import com.intellij.platform.icons.impl.intellij.testIcons
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.margin
import org.junit.jupiter.api.Test

class MarginModifierTest {
  @Test
  fun `should properly apply margin modifier`() {
    testIcons {
      val imgA = testImage(20, 20)

      val result = pretendToRender(
        icon {
          row {
            image(imgA, modifier = IconModifier.margin(1.dp, 2.dp, 3.dp, 4.dp))
          }
        }
      )

      result.assertSize(24, 26)
      result.assertImage(1, 2, 20, 20, imgA)
    }
  }
}
