// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.imageFileIconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.scale.fitArea
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.scale.JBUIScale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.ImageIcon

/**
 * The renderer of a [SwingIcon] applies the IDE zoom to every unit of an icon, as Swing applies it to logical pixels. An
 * image is measured at its design size, so `dp` units and image sizes follow the zoom alike.
 */
@TestApplication
class SwingIconZoomTest {
  @TempDir
  lateinit var dir: Path

  @BeforeEach
  fun activateIconManager() {
    IntelliJIconManager.activate()
  }

  @Test
  fun `an image follows the IDE zoom, as a legacy icon does`() {
    val icon = SwingIcon(redSquare(16))

    withZoom(1.25f) {
      assertEquals(20, icon.iconWidth)
      assertEquals(20, icon.iconHeight)
      assertEquals(20 * 20, countRed(paint(icon)), "the whole zoomed square must be painted")
    }
  }

  @Test
  fun `an icon is measured again after a zoom change`() {
    val icon = SwingIcon(redSquare(16))
    assertEquals(16, icon.iconWidth)

    withZoom(2f) {
      assertEquals(32, icon.iconWidth)
    }
    assertEquals(16, icon.iconWidth)
  }

  @Test
  fun `a limited fit in dp follows the zoom like the image it fits`() {
    val fit = IconModifier.scale(fitArea(18.dp, 18.dp, upscale = false))

    withZoom(1.25f) {
      // a 16px image is smaller than 18dp at every zoom, so it keeps its zoomed size
      assertEquals(JBUIScale.scale(16), SwingIcon(redSquare(16, fit)).iconWidth)
      // a 36px image is fitted into 18dp, zoomed
      assertEquals(JBUIScale.scale(18), SwingIcon(redSquare(36, fit)).iconWidth)
    }
  }

  /** A legacy icon of a fixed size does not scale with the zoom. Its odd size is not rounded to an even one. */
  @Test
  fun `a legacy icon of a fixed odd size keeps its size at each zoom`() {
    val legacy = ImageIcon(BufferedImage(17, 17, BufferedImage.TYPE_INT_ARGB))
    val icon = SwingIcon(DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, IconModifier))))

    for (zoom in listOf(1f, 1.5f, 2f)) {
      withZoom(zoom) {
        assertEquals(17 to 17, icon.iconWidth to icon.iconHeight, "zoom $zoom")
      }
    }
  }

  /** A factor of a parent layer scales a legacy icon of a fixed size, but the zoom does not. The whole icon is painted. */
  @Test
  fun `a legacy icon of a fixed size follows a parent factor but not the zoom`() {
    val red = BufferedImage(16, 12, BufferedImage.TYPE_INT_ARGB).also { image ->
      for (y in 0 until 12) for (x in 0 until 16) image.setRGB(x, y, 0xFFFF0000.toInt())
    }
    val legacy = DefaultLayeredIconDescriptor(listOf(SwingIconLayer(ImageIcon(red), IconModifier)))
    val icon = SwingIcon(DefaultLayeredIconDescriptor(listOf(NestedIconLayer(legacy, IconModifier.scale(factor(2f))))))

    for (zoom in listOf(1f, 1.5f)) {
      withZoom(zoom) {
        assertEquals(32 to 24, icon.iconWidth to icon.iconHeight, "zoom $zoom")
        assertEquals(32 * 24, countRed(paint(icon)), "zoom $zoom: the whole icon must be painted")
      }
    }
  }

  private fun redSquare(size: Int, modifier: IconModifier = IconModifier): IconDescriptor {
    val file = dir.resolve("red$size.svg")
    Files.writeString(file, """<svg xmlns="http://www.w3.org/2000/svg" width="$size" height="$size"><rect width="$size" height="$size" fill="#FF0000"/></svg>""")
    return imageFileIconDescriptor(file, modifier)
  }

  private fun paint(icon: SwingIcon): BufferedImage {
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

  private fun countRed(image: BufferedImage): Int =
    (0 until image.height).sumOf { y -> (0 until image.width).count { x -> image.getRGB(x, y) == 0xFFFF0000.toInt() } }
}

/** Runs [block] at the IDE zoom [zoom], and restores the zoom after it. */
internal fun withZoom(zoom: Float, block: () -> Unit) {
  val previous = JBUIScale.scale(1f)
  JBUIScale.setUserScaleFactorForTest(zoom)
  try {
    block()
  }
  finally {
    JBUIScale.setUserScaleFactorForTest(previous)
  }
}
