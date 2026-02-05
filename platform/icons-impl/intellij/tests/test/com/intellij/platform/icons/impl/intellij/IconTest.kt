// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.icon
import com.intellij.platform.icons.imageIcon
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.scale.fitArea
import com.intellij.platform.icons.swing.toSwingIcon
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

@TestApplication
class IconTest {
  @Test
  fun `should properly animate icon`() {
    testIcons {
      val imgA = testImage(20, 20)
      val imgB = testImage(20, 20)

      val run = createTestRun(
        icon {
          animation {
            frame(500) {
              image(imgA)
            }
            frame(500) {
              image(imgB)
            }
          }
        }
      )

      run.performRender().assertImage(0, 0, 20, 20, imgA)
      Thread.sleep(500)
      run.performRender().assertImage(0, 0, 20, 20, imgB)
    }
  }

  @Test
  fun `should properly calculate row layout`() {
    testIcons {
      val imgA = testImage(20, 20)
      val imgB = testImage(5, 5)

      val result = pretendToRender(
        icon {
          row {
            image(imgA)
            image(imgB)
          }
        }
      )

      result.assertSize(25, 20)
      result.assertImage(0, 0, 20, 20, imgA)
      result.assertImage(20, 0, 5, 5, imgB)
    }
  }

  @Test
  fun `should properly calculate column layout`() {
    testIcons {
      val imgA = testImage(20, 20)
      val imgB = testImage(5, 5)

      val result = pretendToRender(
        icon {
          column {
            image(imgA)
            image(imgB)
          }
        }
      )

      result.assertSize(20, 25)
      result.assertImage(0, 0, 20, 20, imgA)
      result.assertImage(0, 20, 5, 5, imgB)
    }
  }

  @Test
  fun `should properly calculate box layout`() {
    testIcons {
      val imgA = testImage(20, 20)
      val imgB = testImage(5, 5)

      val result = pretendToRender(
        icon {
          box {
            image(imgA)
            image(imgB)
          }
        }
      )

      result.assertSize(20, 20)
      result.assertImage(0, 0, 20, 20, imgA)
      result.assertImage(0, 0, 5, 5, imgB)
    }
  }

  @Test
  fun `should properly scale nested scaled image`() {
    testIcons {
      val imgA = testImage(20, 20)
      val imgB = testImage(20, 20)

      val result = pretendToRender(
        icon {
          image(imgA)
          image(imgB, IconModifier.scale(fitArea(5.dp, 5.dp)))
        }
      )

      result.assertSize(20, 20)
      result.assertImage(0, 0, 20, 20, imgA)
      result.assertImage(0, 0, 5, 5, imgB)
    }
  }
  @Test
  fun `should properly apply global scale to nested image`() {
    testIcons {
      val imgA = testImage(5, 5)

      val result = pretendToRender(
        icon {
          image(imgA, IconModifier.scale(fitArea(10.dp, 10.dp)))
          image(imgA, IconModifier.scale(fitArea(10.dp, 10.dp, relative = false)))
        },
        fitArea(15.dp, 15.dp)
      )

      result.assertSize(15, 15)
      result.assertImage(0, 0, 15, 15, imgA)
      result.assertImage(0, 0, 10, 10, imgA)
    }
  }

  @Test
  fun `should render icon to buffered image`() {
    IntelliJIconManager.activate()
    val icon = imageIcon("actions/addFile.svg", IconTest::class.java.classLoader)

    val swingIcon = icon.toSwingIcon()
    val image = BufferedImage(swingIcon.iconWidth, swingIcon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val g2 = image.createGraphics()
    try {
      swingIcon.paintIcon(null, g2, 0, 0)
    } finally {
      g2.dispose()
    }
  }
}
