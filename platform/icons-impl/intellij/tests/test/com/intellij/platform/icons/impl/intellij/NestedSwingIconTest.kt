// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.deferredIconDescriptor
import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.align
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.scale.fitArea
import com.intellij.platform.icons.impl.intellij.rendering.withZoom
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.DeferredIcon
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon

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

  /** A layer that holds a deferred icon deeper inside, in a nested icon or in a column, takes the size of the resolved icon. */
  @Test
  fun `a deferred icon at any depth updates the size of the layers around it`() = runInEdtAndWait {
    val deferred = deferredIconDescriptor(imageIconDescriptor(TEST, classLoader)) { imageIconDescriptor(WIDE, classLoader) }
    val nested = SwingIcon(iconDescriptor { icon(iconDescriptor { icon(deferred) }) }).createStrokeIcon(Color.RED)
    val inColumn = SwingIcon(iconDescriptor { column { icon(deferred); icon(imageIconDescriptor(TEST, classLoader)) } })
    for (wrapped in listOf(nested, inColumn)) {
      paint(wrapped)
    }

    PlatformTestUtil.waitWithEventsDispatching("the deferred icon was not resolved", {
      paint(nested)
      nested.iconHeight == 8
    }, 10)
    assertEquals(16, nested.iconWidth, "the resolved icon is 16x8")
    paint(inColumn)
    assertEquals(24, inColumn.iconHeight, "the column holds the resolved 16x8 icon and a 16x16 icon")
  }

  /**
   * A fit with `relative = false` ignores the IDE zoom, but a factor on the whole icon scales it. The limits `upscale`
   * and `downscale` do not change that: here the fit shrinks the icon, so the limit `upscale = false` has no effect.
   */
  @Test
  fun `a factor on a fit that is not relative scales the fitted icon, with or without a limit`() = runInEdtAndWait {
    for (upscale in listOf(true, false)) {
      val descriptor = iconDescriptor { image(TEST, classLoader, IconModifier.scale(fitArea(8.dp, 8.dp, relative = false, upscale = upscale))) }
      val icon = descriptor.createSwingIcon().scaled(factor(2f)) as Icon

      val result = paint(icon)

      assertEquals(16, icon.iconWidth, "upscale = $upscale")
      assertEquals(Rectangle(8, 8, 16, 16), result, "upscale = $upscale: the red square fills the middle of the icon at density 2")
    }
  }

  /**
   * A fit with `relative = false` in a row, in a column or in a nested icon ignores the IDE zoom and the factor of a parent
   * layer, as when it paints. Here the other icon is 8dp, so 12 at the zoom 1.5.
   */
  @Test
  fun `a fit that is not relative measures its own size in a row, a column and a nested icon`() {
    val notRelative = IconModifier.scale(fitArea(16.dp, 16.dp, relative = false))
    val small = IconModifier.scale(fitArea(8.dp, 8.dp))
    val inRow = SwingIcon(iconDescriptor { row { image(TEST, classLoader, notRelative); image(TEST, classLoader, small) } })
    val inColumn = SwingIcon(iconDescriptor { column { image(TEST, classLoader, notRelative); image(TEST, classLoader, small) } })
    val inFactor = SwingIcon(iconDescriptor { icon(iconDescriptor { image(TEST, classLoader, notRelative) }, IconModifier.scale(factor(2f))) })
    val limitedInFactor = SwingIcon(iconDescriptor {
      icon(iconDescriptor { image(TEST, classLoader, IconModifier.scale(fitArea(8.dp, 8.dp, relative = false, upscale = false))) },
           IconModifier.scale(factor(2f)))
    })

    withZoom(1.5f) {
      assertEquals(28 to 16, inRow.iconWidth to inRow.iconHeight, "the row")
      assertEquals(16 to 28, inColumn.iconWidth to inColumn.iconHeight, "the column")
      assertEquals(16 to 16, inFactor.iconWidth to inFactor.iconHeight, "the nested icon with a factor")
      assertEquals(8 to 8, limitedInFactor.iconWidth to limitedInFactor.iconHeight, "the limited fit in a nested icon with a factor")
    }
  }

  /**
   * A box with a factor holds a fit with `relative = false` and a badge at the bottom right. The box passes its measured
   * size to the badge, so the badge is painted inside the icon.
   */
  @Test
  fun `a box with a factor places a badge inside a fit that is not relative`() = runInEdtAndWait {
    val icon = SwingIcon(iconDescriptor {
      box(IconModifier.scale(factor(2f))) {
        image(TEST, classLoader, IconModifier.scale(fitArea(24.dp, 24.dp, relative = false)))
        image(TEST, classLoader, IconModifier.scale(fitArea(4.dp, 4.dp)).align(IconAlign.BottomRight))
      }
    })

    assertEquals(24 to 24, icon.iconWidth to icon.iconHeight)
    // at density 2, the red square of the icon is at 12..35, and the red square of the 8x8 badge is at 36..43
    assertEquals(Rectangle(12, 12, 32, 32), paint(icon))
  }

  /** A row and a column with a factor place each layer after the space that the layer before it paints. */
  @Test
  fun `a row and a column with a factor do not overlap their layers`() = runInEdtAndWait {
    val inRow = SwingIcon(iconDescriptor { row(IconModifier.scale(factor(2f))) { image(TEST, classLoader); image(TEST, classLoader) } })
    val inColumn = SwingIcon(iconDescriptor { column(IconModifier.scale(factor(2f))) { image(TEST, classLoader); image(TEST, classLoader) } })

    assertEquals(64 to 32, inRow.iconWidth to inRow.iconHeight)
    // at density 2, the red squares of the 32x32 layers are at 16..47 and at 80..111
    assertEquals(Rectangle(16, 16, 96, 32), paint(inRow))
    assertEquals(32 to 64, inColumn.iconWidth to inColumn.iconHeight)
    assertEquals(Rectangle(16, 16, 32, 96), paint(inColumn))
  }

  /** A bridged deferred icon that resolves after the measure and before the paint makes the icon measure again. */
  @Test
  fun `a bridged deferred icon that resolves before the paint updates the size`() = runInEdtAndWait {
    val deferred = ResolvedLater(placeholder = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), result = BufferedImage(24, 12, BufferedImage.TYPE_INT_ARGB))
    val icon = SwingIcon(DefaultLayeredIconDescriptor(listOf(SwingIconLayer(deferred, IconModifier))))
    assertEquals(16 to 16, icon.iconWidth to icon.iconHeight, "the placeholder")

    deferred.isDone = true
    paint(icon)

    PlatformTestUtil.waitWithEventsDispatching("the icon was not measured again", { icon.iconWidth == 24 }, 10)
    assertEquals(12, icon.iconHeight)
  }

  /** A deferred icon that is resolved when [isDone] is set. */
  private class ResolvedLater(placeholder: BufferedImage, result: BufferedImage) : DeferredIcon {
    override val baseIcon: Icon = ImageIcon(placeholder)
    private val result: Icon = ImageIcon(result)
    override var isDone: Boolean = false
    private val current: Icon get() = if (isDone) result else baseIcon

    override fun evaluate(): Icon = result
    override fun notifyPaint(c: Component, x: Int, y: Int) = Unit
    override fun getModificationCount(): Long = if (isDone) 1 else 0
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) = current.paintIcon(c, g, x, y)
    override fun getIconWidth(): Int = current.iconWidth
    override fun getIconHeight(): Int = current.iconHeight
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
