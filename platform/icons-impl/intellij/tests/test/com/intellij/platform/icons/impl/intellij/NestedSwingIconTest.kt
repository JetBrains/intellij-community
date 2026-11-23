// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.deferredIconDescriptor
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.scale.fitArea
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.Icon

/**
 * A `SwingIcon` that wraps its descriptor in a nested icon: the stroke icon, and a factor on a limited fit.
 *
 * Test artwork (iconsTest/variant): `test.svg` 16x16 with an 8x8 red square at (4, 4); `wide.svg` 16x8.
 */
@TestApplication
class NestedSwingIconTest {
  private var previousIconManager: IconManager? = null
  private var previousRendererManager: IconRendererManager? = null
  private val classLoader: ClassLoader = NestedSwingIconTest::class.java.classLoader

  @BeforeEach
  fun setUp() {
    previousIconManager = runCatching { IconManager.getInstance() }.getOrNull()
    previousRendererManager = runCatching { IconRendererManager.getInstance() }.getOrNull()
    IntelliJIconManager.activate()
    IconLoader.activate()
  }

  @AfterEach
  fun tearDown() {
    IconLoader.deactivate()
    previousIconManager?.let { IconManager.activate(it) }
    previousRendererManager?.let { IconRendererManager.activate(it) }
  }

  @Test
  fun `a deferred icon can be stroked and fitted, and takes the size of the resolved icon`() = runInEdtAndWait {
    val deferred = deferredIconDescriptor(imageIconDescriptor(TEST, classLoader)) { imageIconDescriptor(WIDE, classLoader) }
    val icon = deferred.createSwingIcon() as SwingIcon
    val stroked = icon.createStrokeIcon(Color.RED)
    val fitted = icon.scaled(fitArea(32.dp, 32.dp, upscale = false)).scaled(factor(2f)) as Icon
    // the first paint schedules the evaluation, and the icon is 16x16 until the evaluation is done
    for (wrapped in listOf(stroked, fitted)) {
      paint(wrapped)
    }

    PlatformTestUtil.waitWithEventsDispatching("the deferred icon was not resolved", {
      paint(stroked)
      stroked.iconHeight == 8
    }, 10)
    assertEquals(16, stroked.iconWidth, "the resolved icon is 16x8")
    paint(fitted)
    assertEquals(32 to 16, fitted.iconWidth to fitted.iconHeight, "the resolved icon is 16x8, twice")
  }

  /**
   * A fit with `relative = false` does not follow the icon scaling (see `fitArea`). A factor on such an icon gives the same
   * size when it is measured and when it is painted.
   */
  @Test
  fun `a factor on a limited fit that is not relative is measured as it is painted`() = runInEdtAndWait {
    val descriptor = iconDescriptor { image(TEST, classLoader, IconModifier.scale(fitArea(8.dp, 8.dp, relative = false, upscale = false))) }
    val icon = descriptor.createSwingIcon().scaled(factor(2f)) as Icon

    val result = paint(icon)

    assertEquals(8, icon.iconWidth)
    assertEquals(Rectangle(4, 4, 8, 8), result, "the red square fills the middle of the icon at density 2")
  }

  /** The bounds of the opaque pixels after painting [icon] at density 2. */
  private fun paint(icon: Icon): Rectangle? {
    val width = maxOf(icon.iconWidth * 2, 1)
    val height = maxOf(icon.iconHeight * 2, 1)
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      g.scale(2.0, 2.0)
      icon.paintIcon(null, g, 0, 0)
    }
    finally {
      g.dispose()
    }
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = -1
    var maxY = -1
    for (y in 0 until height) {
      for (x in 0 until width) {
        if ((image.getRGB(x, y) ushr 24) != 0) {
          minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
        }
      }
    }
    return if (maxX < 0) null else Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
  }

  private companion object {
    const val TEST = "iconsTest/variant/test.svg"
    const val WIDE = "iconsTest/variant/wide.svg"
  }
}
