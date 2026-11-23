// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.design.sRGB
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.stroke
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.icons.IconReplacer
import com.intellij.ui.icons.ReplaceableIcon
import com.intellij.ui.icons.toStrokeIcon
import java.awt.Component
import java.awt.Graphics
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.Icon
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf

/**
 * `toStrokeIcon` on icons of the icons API: a look that strokes its buttons' icons (the run widget's) gets the same
 * stroke icon for a `SwingIcon` as for a legacy icon — the hand-drawn `_stroke` variant in the stroke color, not the
 * icon's filled artwork.
 */
@TestApplication
class StrokeIconBridgeTest {
  private val classLoader = StrokeIconBridgeTest::class.java.classLoader

  @BeforeEach
  fun activateIconManager() {
    IntelliJIconManager.activate()
  }

  @Test
  fun `a SwingIcon is stroked through the icons API, from its stroke variant`() {
    val run = imageIconDescriptor("expui/run/run.svg", classLoader).createSwingIcon()
    assertTrue(countGreen(render(run)) > 0, "precondition: the run icon is filled green")

    val stroked = toStrokeIcon(run, Color.RED)

    assertInstanceOf<SwingIcon>(stroked)
    val image = render(stroked)
    assertTrue(countRed(image) > 0, "the stroke icon must be drawn in the stroke color")
    assertEquals(0, countGreen(image), "the stroke icon must not keep the green fill of the run icon")
    // the hand-drawn stroke variant, not an outline derived from run.svg
    val variant = imageIconDescriptor("expui/run/run_stroke.svg", classLoader, IconModifier.stroke(sRGB("#FF0000"))).createSwingIcon()
    assertArrayEquals(pixels(render(variant)), pixels(image))
  }

  @Test
  fun `a SwingIcon inside a legacy composite icon is stroked too`() {
    val run = imageIconDescriptor("expui/run/run.svg", classLoader).createSwingIcon()

    // like LayeredIcon or RowIcon: toStrokeIcon reaches the icons inside through ReplaceableIcon
    val stroked = toStrokeIcon(WrapperIcon(run), Color.RED)

    val image = render(stroked)
    assertTrue(countRed(image) > 0, "the nested SwingIcon must be stroked")
    assertEquals(0, countGreen(image))
  }

  /**
   * A legacy composite that is bridged into the icons API gets the stroke in each `CachedImageIcon` in it, as the legacy
   * `toStrokeIcon` strokes the composite itself.
   */
  @Test
  fun `a bridged legacy composite icon is stroked in the icons inside, as by the legacy toStrokeIcon`() {
    IconLoader.activate()
    try {
      val run = IconLoader.findIcon("expui/run/run.svg", classLoader)!!
      val bridged = SwingIcon(DefaultLayeredIconDescriptor(listOf(SwingIconLayer(WrapperIcon(run), IconModifier))))
      val legacy = pixels(render(toStrokeIcon(WrapperIcon(run), Color.RED)))
      assertFalse(legacy.contentEquals(pixels(render(run))), "precondition: the legacy stroke icon differs from the icon")

      val stroked = pixels(render(toStrokeIcon(bridged, Color.RED)))

      assertArrayEquals(legacy, stroked)
    }
    finally {
      IconLoader.deactivate()
    }
  }

  private fun render(icon: Icon): BufferedImage {
    val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      icon.paintIcon(null, g, 0, 0)
    }
    finally {
      g.dispose()
    }
    return image
  }

  private fun pixels(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

  private fun countRed(image: BufferedImage): Int = pixels(image).count { opaque(it) && red(it) > 200 && green(it) < 80 && blue(it) < 80 }

  private fun countGreen(image: BufferedImage): Int = pixels(image).count { opaque(it) && green(it) > red(it) + 40 && green(it) > blue(it) + 40 }

  private fun opaque(argb: Int) = argb ushr 24 and 0xFF >= 128
  private fun red(argb: Int) = argb ushr 16 and 0xFF
  private fun green(argb: Int) = argb ushr 8 and 0xFF
  private fun blue(argb: Int) = argb and 0xFF
}

/** A legacy composite icon in the smallest form: one icon inside, replaceable like `LayeredIcon` or `RowIcon`. */
private class WrapperIcon(private val inner: Icon) : ReplaceableIcon {
  override fun replaceBy(replacer: IconReplacer): Icon = WrapperIcon(replacer.replaceIcon(inner))
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) = inner.paintIcon(c, g, x, y)
  override fun getIconWidth(): Int = inner.iconWidth
  override fun getIconHeight(): Int = inner.iconHeight
}
