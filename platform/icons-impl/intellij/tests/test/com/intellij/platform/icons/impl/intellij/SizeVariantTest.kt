// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.scale.JBUIScale
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.util.zip.CRC32
import javax.swing.Icon

/**
 * `@WxH` size variants in the new icons API, at parity with the legacy `loadIconCustomVersion`: an image painted at a
 * logical size W×H (density and IDE zoom divided out) that has a hand-drawn `<path>@WxH.svg` is drawn from that file.
 *
 * Test artwork (iconsTest/variant): `test.svg` 16×16 with an 8×8 red square at (4, 4) (`_dark`: green); `test@20x20.svg`
 * 20×20 with an 18×18 blue square at (1, 1) (`_dark`: yellow); `wide.svg` 16×8 / `wide@20x10.svg` alike.
 */
@TestApplication
class SizeVariantTest {
  private var previousIconManager: IconManager? = null
  private var previousRendererManager: IconRendererManager? = null
  private var previousZoom = 1f
  private val classLoader: ClassLoader = SizeVariantTest::class.java.classLoader

  @BeforeEach
  fun setUp() {
    previousIconManager = runCatching { IconManager.getInstance() }.getOrNull()
    previousRendererManager = runCatching { IconRendererManager.getInstance() }.getOrNull()
    previousZoom = JBUIScale.scale(1f)
    IntelliJIconManager.activate()
    IconLoader.activate()
  }

  @AfterEach
  fun tearDown() {
    JBUIScale.setUserScaleFactorForTest(previousZoom)
    IconLoader.deactivate()
    previousIconManager?.let { IconManager.activate(it) }
    previousRendererManager?.let { IconRendererManager.activate(it) }
  }

  @Test
  fun `native descriptor scaled to the variant size renders the variant`() = runInEdtAndWait {
    val result = paint(native(TEST).scaled(factor(1.25f)) as Icon)
    assertEquals(Rectangle(0, 0, 40, 40), result.frame)
    assertEquals(Rectangle(2, 2, 36, 36), result.content)
    assertEquals(BLUE, result.centerColor)
  }

  @Test
  fun `native descriptor at its own size renders the base`() = runInEdtAndWait {
    val result = paint(native(TEST))
    assertEquals(Rectangle(0, 0, 32, 32), result.frame)
    assertEquals(Rectangle(8, 8, 16, 16), result.content)
    assertEquals(RED, result.centerColor)
  }

  @Test
  fun `a size without a variant renders the scaled base`() = runInEdtAndWait {
    val result = paint(native(TEST).scaled(factor(1.5f)) as Icon)
    assertEquals(Rectangle(0, 0, 48, 48), result.frame)
    assertEquals(Rectangle(12, 12, 24, 24), result.content)
    assertEquals(RED, result.centerColor)
  }

  @Test
  fun `non-square variants are matched by width and height`() = runInEdtAndWait {
    val result = paint(native(WIDE).scaled(factor(1.25f)) as Icon)
    assertEquals(Rectangle(0, 0, 40, 20), result.frame)
    assertEquals(Rectangle(2, 2, 36, 16), result.content)
    assertEquals(BLUE, result.centerColor)
  }

  @Test
  fun `bridged legacy icon scaled to the variant size renders the variant, dark when the legacy icon is dark`() = runInEdtAndWait {
    val legacy = IconLoader.findIcon(TEST, classLoader) as CachedImageIcon
    val light = paint(bridged(legacy).scale(1.25f))
    assertEquals(Rectangle(0, 0, 40, 40), light.frame)
    assertEquals(Rectangle(2, 2, 36, 36), light.content)
    assertEquals(BLUE, light.centerColor)

    val dark = paint(bridged(IconLoader.getDarkIcon(legacy, true)).scale(1.25f))
    assertEquals(Rectangle(2, 2, 36, 36), dark.content)
    assertEquals(YELLOW, dark.centerColor)

    val native = paint(bridged(legacy))
    assertEquals(Rectangle(8, 8, 16, 16), native.content)
    assertEquals(RED, native.centerColor)
  }

  @Test
  fun `IDE zoom 1_06 with the main toolbar's factor still selects the variant`() = runInEdtAndWait {
    JBUIScale.setUserScaleFactorForTest(1.06f)
    for ((label, icon) in listOf("native" to native(TEST), "bridged" to bridged(IconLoader.findIcon(TEST, classLoader)!!))) {
      // HeaderToolbarButtonLook → loadIconCustomVersionOrScale(ScalableIcon, 20): scale(JBUIScale.scale(1f) * 20 / iconWidth)
      val scaled = icon.scale(JBUIScale.scale(1f) * 20 / icon.iconWidth)
      val result = paint(scaled)
      assertEquals(BLUE, result.centerColor, label)
    }
  }

  /**
   * The main toolbar asks for 20x20 with `loadIconCustomVersionOrScale(icon, 20)`, which scales by `zoom * 20 / iconWidth`.
   * The `iconWidth` and the device pixels of the box are rounded. The variant is chosen as the legacy icons choose it, also
   * when the device pixels are odd or the zoom is not a multiple of 1/16.
   */
  @Test
  fun `the main toolbar's factor selects the variant at each IDE zoom and density`() = runInEdtAndWait {
    for (zoom in listOf(0.9f, 1f, 1.1f, 1.15f, 1.2f, 1.25f, 1.4f, 1.5f, 2f)) {
      JBUIScale.setUserScaleFactorForTest(zoom)
      for (density in listOf(1.0, 1.25, 1.5, 2.0)) {
        for ((label, icon) in listOf("native" to native(TEST), "bridged" to bridged(IconLoader.findIcon(TEST, classLoader)!!))) {
          val scaled = icon.scale(JBUIScale.scale(1f) * 20 / icon.iconWidth)
          assertEquals(BLUE, paint(scaled, density).centerColor, "$label at zoom $zoom and density $density")
        }
      }
    }
  }

  @Test
  fun `a direct factor selects the variant at a fractional density`() = runInEdtAndWait {
    JBUIScale.setUserScaleFactorForTest(1.25f)
    for (density in listOf(1.25, 1.5)) {
      assertEquals(BLUE, paint(native(TEST).scaled(factor(1.25f)) as Icon, density).centerColor, "density $density")
    }
  }

  /** `loadIconCustomVersionOrScale` divides by the width of the icon, which is 0 for an icon without an image. */
  @Test
  fun `an icon without an image ignores a factor that is not finite or not positive`() = runInEdtAndWait {
    val empty = SwingIcon(DefaultLayeredIconDescriptor(emptyList()))
    for (factor in listOf(JBUIScale.scale(1f) * 20 / (empty.iconWidth * 1f), Float.NaN, 0f, -1f)) {
      val scaled = empty.scale(factor)
      assertSame(empty, scaled, "factor $factor")
      assertEquals(0, scaled.iconWidth)
      val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
      val g = image.createGraphics()
      try {
        scaled.paintIcon(null, g, 0, 0)
      }
      finally {
        g.dispose()
      }
    }
  }

  /**
   * Native-size output must be pixel-identical to the output before the ScaleContext split (OBJ_SCALE = total factor,
   * USR = SYS = 1). The checksums were recorded with that implementation, after the renderer started to apply the IDE zoom.
   */
  @Test
  fun `native size output is unchanged by the scale context split`() = runInEdtAndWait {
    val actual = LinkedHashMap<String, Long>()
    for (zoom in listOf(1f, 1.06f)) {
      JBUIScale.setUserScaleFactorForTest(zoom)
      for (density in listOf(1.0, 2.0)) {
        actual["native search zoom=$zoom density=$density"] = paint(native(SEARCH), density).checksum
        actual["bridged search zoom=$zoom density=$density"] =
          paint(bridged(IconLoader.findIcon(SEARCH, classLoader)!!), density).checksum
        actual["native test zoom=$zoom density=$density"] = paint(native(TEST), density).checksum
      }
    }
    assertEquals(GOLDEN, actual)
  }

  private fun native(path: String): SwingIcon = imageIconDescriptor(path, classLoader).createSwingIcon() as SwingIcon

  private fun bridged(legacy: Icon): SwingIcon = SwingIcon(DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, IconModifier))))

  private class Painted(val frame: Rectangle, val content: Rectangle?, val centerColor: Int, val checksum: Long)

  private fun paint(icon: Icon, density: Double = 2.0): Painted {
    val width = Math.round(icon.iconWidth * density).toInt()
    val height = Math.round(icon.iconHeight * density).toInt()
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      g.scale(density, density)
      icon.paintIcon(null, g, 0, 0)
    }
    finally {
      g.dispose()
    }
    val pixels = image.getRGB(0, 0, width, height, null, 0, width)
    val crc = CRC32()
    for (pixel in pixels) {
      crc.update(pixel ushr 24)
      crc.update(pixel ushr 16)
      crc.update(pixel ushr 8)
      crc.update(pixel)
    }
    return Painted(Rectangle(0, 0, width, height), alphaBounds(image), image.getRGB(width / 2, height / 2), crc.value)
  }

  private fun alphaBounds(image: BufferedImage): Rectangle? {
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = -1
    var maxY = -1
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
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
    const val SEARCH = "expui/general/search.svg"
    const val RED = 0xFFFF0000.toInt()
    const val BLUE = 0xFF0000FF.toInt()
    const val YELLOW = 0xFFFFFF00.toInt()

    /** Recorded with the implementation before the change (OBJ_SCALE = total factor, USR = SYS = 1). */
    val GOLDEN: Map<String, Long> = mapOf(
      "native search zoom=1.0 density=1.0" to 4288686838L,
      "bridged search zoom=1.0 density=1.0" to 4288686838L,
      "native test zoom=1.0 density=1.0" to 2354882512L,
      "native search zoom=1.0 density=2.0" to 466478299L,
      "bridged search zoom=1.0 density=2.0" to 466478299L,
      "native test zoom=1.0 density=2.0" to 838810232L,
      "native search zoom=1.06 density=1.0" to 237906651L,
      "bridged search zoom=1.06 density=1.0" to 237906651L,
      "native test zoom=1.06 density=1.0" to 1774991544L,
      "native search zoom=1.06 density=2.0" to 11816361L,
      "bridged search zoom=1.06 density=2.0" to 11816361L,
      "native test zoom=1.06 density=2.0" to 341020059L,
    )
  }
}
