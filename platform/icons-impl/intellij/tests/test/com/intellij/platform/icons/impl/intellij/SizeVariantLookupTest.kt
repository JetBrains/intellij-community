// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.intellij.rendering.images.DataLoaderImageResourceHolder
import com.intellij.platform.icons.impl.intellij.rendering.images.LegacyIconImageResourceHolder
import com.intellij.platform.icons.impl.intellij.rendering.images.SwingImageResourceHolder
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.PaintedSize
import com.intellij.platform.icons.impl.rendering.SizeVariantCache
import com.intellij.platform.icons.impl.rendering.SizeVariants
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.findIconLoaderByPath
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Image
import java.awt.image.BufferedImage
import javax.swing.Icon

/**
 * How the Swing holders look up a `@WxH` size variant, at parity with legacy `findIconCustomVersion`.
 *
 * Test artwork (iconsTest/variant): `test.svg` 16×16 (red) with `test@20x20.svg` (blue). `sized.svg` 16×16 (red, `_dark`:
 * green) with a light `sized@20x20.svg` that is 24×24 (blue) and a dark `sized@20x20_dark.svg` that is 20×20 (yellow).
 * `patched.svg` 16×16 (red) has no variant of its own.
 */
@TestApplication
class SizeVariantLookupTest {
  private var previousIconManager: IconManager? = null
  private var previousRendererManager: IconRendererManager? = null
  private val classLoader: ClassLoader = SizeVariantLookupTest::class.java.classLoader

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

  /** A direct factor paints at `W * zoom`: `factor(1.25f)` of a 16×16 icon at the zoom 1.06 paints at 21.2. */
  @Test
  fun `Exact selects the variant of a direct factor at a zoom that is not a multiple of 1 over 16`() {
    val variant = SizeVariants.chooseVariant(SizeVariantPolicy.Exact, PaintedSize(21.2, 21.2), 1.06, 16, 16) { _, _ -> true }

    assertEquals(20 to 20, variant)
  }

  /**
   * Without the JRE HiDPI mode, the density is not part of the scale context. Then a 16×16 icon painted at 20×20 logical
   * pixels on a display of the density 1.25 has the object scale 1. The variant is chosen from the painted size.
   */
  @Test
  fun `a variant is chosen also at the object scale 1`() = runInEdtAndWait {
    val holders = listOf(
      DataLoaderImageResourceHolder(findIconLoaderByPath(TEST, classLoader)),
      LegacyIconImageResourceHolder(IconLoader.findIcon(TEST, classLoader)!!),
    )
    for (holder in holders) {
      val image = holder.image(objScale = 1.0, painted = 20.0, DefaultImageModifiers())

      assertEquals(BLUE, center(image), holder.javaClass.simpleName)
    }
  }

  /** The variant is checked in the artwork that the modifiers select, here the dark one. */
  @Test
  fun `a variant is checked in the dark artwork for a dark image`() = runInEdtAndWait {
    val holder = DataLoaderImageResourceHolder(findIconLoaderByPath(SIZED, classLoader))

    assertEquals(YELLOW, center(holder.image(objScale = 1.25, painted = 20.0, DefaultImageModifiers(isDark = true))))
    assertEquals(16 to 16, holder.getExpectedDimensions(DefaultImageModifiers(isDark = true)).let { it.width to it.height })
    // the light variant is 24×24, so it is not drawn for 20×20, and the light base is scaled
    assertEquals(RED, center(holder.image(objScale = 1.25, painted = 20.0, DefaultImageModifiers(isDark = false))))
  }

  /** A path patcher, for example the one of a theme, can give a variant that does not exist as a resource. */
  @Test
  fun `a path patcher can give a variant`() = runInEdtAndWait {
    val patcher = object : IconPathPatcher() {
      override fun patchPath(path: String, classLoader: ClassLoader?): String? =
        if (path.removePrefix("/") == PATCHED_VARIANT) "/$TEST_VARIANT" else null
    }
    IconLoader.installPathPatcher(patcher)
    try {
      val native = imageIconDescriptor(PATCHED, classLoader).createSwingIcon() as SwingIcon
      val bridged = SwingIcon(DefaultLayeredIconDescriptor(listOf(SwingIconLayer(IconLoader.findIcon(PATCHED, classLoader)!!, IconModifier))))
      for ((label, icon) in listOf("native" to native, "bridged" to bridged)) {
        assertEquals(BLUE, center(paint(icon.scaled(factor(1.25f)) as Icon)), label)
      }
    }
    finally {
      IconLoader.removePathPatcher(patcher)
    }
  }

  /** A bridged legacy icon that has its own object scale, and no variant, is rasterized at its scaled size. */
  @Test
  fun `a scaled legacy icon is rasterized at its scaled size`() = runInEdtAndWait {
    val scaled = (IconLoader.findIcon(PATCHED, classLoader) as CachedImageIcon).scale(2f)
    val holder = LegacyIconImageResourceHolder(scaled)

    assertEquals(32 to 32, holder.getExpectedDimensions(null).let { it.width to it.height })
    val image = holder.image(objScale = 1.0, painted = 32.0, DefaultImageModifiers())!!
    assertEquals(32 to 32, image.getWidth(null) to image.getHeight(null))
  }

  @Test
  fun `the variant cache keeps the variants that were used last`() {
    val cache = SizeVariantCache<String>(limit = 2)
    var loads = 0
    val load = { width: Int, height: Int -> cache.get(width, height) { loads++; if (width == 20) "20" else null } }

    load(20, 20)
    load(14, 14)
    load(20, 20)
    assertEquals(2, loads, "a variant that exists and a variant that does not exist are both kept")
    // 14×14 was used before 20×20, so it is removed
    load(24, 24)
    assertEquals("20", load(20, 20))
    assertEquals(3, loads)
    load(14, 14)
    assertEquals(4, loads, "the variant that was used first is removed")
  }

  private fun SwingImageResourceHolder.image(objScale: Double, painted: Double, modifiers: ImageModifiers): Image? {
    val scale = ScaleContext.of(arrayOf(ScaleType.OBJ_SCALE.of(objScale), ScaleType.USR_SCALE.of(1.0), ScaleType.SYS_SCALE.of(1.0)))
    return getImage(scale, PaintedSize(painted, painted), modifiers, THEME)
  }

  private fun paint(icon: Icon): BufferedImage {
    val image = BufferedImage(icon.iconWidth * 2, icon.iconHeight * 2, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      g.scale(2.0, 2.0)
      icon.paintIcon(null, g, 0, 0)
    }
    finally {
      g.dispose()
    }
    return image
  }

  private fun center(image: Image?): Int {
    val buffered = image as? BufferedImage ?: BufferedImage(image!!.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB).also {
      val g = it.createGraphics()
      try {
        g.drawImage(image, 0, 0, null)
      }
      finally {
        g.dispose()
      }
    }
    return buffered.getRGB(buffered.width / 2, buffered.height / 2)
  }

  private companion object {
    const val TEST = "iconsTest/variant/test.svg"
    const val TEST_VARIANT = "iconsTest/variant/test@20x20.svg"
    const val SIZED = "iconsTest/variant/sized.svg"
    const val PATCHED = "iconsTest/variant/patched.svg"
    const val PATCHED_VARIANT = "iconsTest/variant/patched@20x20.svg"
    const val RED = 0xFFFF0000.toInt()
    const val BLUE = 0xFF0000FF.toInt()
    const val YELLOW = 0xFFFFFF00.toInt()
    val THEME = object : ThemeContext {
      override fun digest(): String = "test"
    }
  }
}
