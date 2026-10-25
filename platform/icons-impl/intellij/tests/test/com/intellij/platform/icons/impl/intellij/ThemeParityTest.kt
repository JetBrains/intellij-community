// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.design.DefaultSRGB
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.modifiers.patchSvg
import com.intellij.platform.icons.modifiers.stroke
import com.intellij.platform.icons.patchers.svgPatcher
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.icons.pathTransform
import com.intellij.ui.svg.newSvgPatcher
import com.intellij.util.SVGLoader
import com.intellij.util.ui.StartupUiUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.awt.image.RGBImageFilter
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.writeText

/**
 * Theme state in the new icons API, at pixel parity with legacy `CachedImageIcon`: the theme color palette
 * (`SVGLoader.colorPatcherProvider`), the global icon filter (`IconLoader.setFilter`) and the theme path patch targets.
 *
 * Test artwork: `iconsTest/theme/palette.svg` is 16×16, the left half `#6c707e`, the right half `#ced0d6`.
 */
@TestApplication
class ThemeParityTest {
  private var previousIconManager: IconManager? = null
  private var previousRendererManager: IconRendererManager? = null
  private var previousPalette: SVGLoader.SvgElementColorPatcherProvider? = null
  private val previousTransform = pathTransform.get()
  private val classLoader: ClassLoader = ThemeParityTest::class.java.classLoader

  @BeforeEach
  fun setUp() {
    previousIconManager = runCatching { IconManager.getInstance() }.getOrNull()
    previousRendererManager = runCatching { IconRendererManager.getInstance() }.getOrNull()
    previousPalette = SVGLoader.colorPatcherProvider
    IntelliJIconManager.activate()
    IconLoader.activate()
    IconLoader.clearCacheInTests()
  }

  @AfterEach
  fun tearDown() {
    SVGLoader.colorPatcherProvider = previousPalette
    pathTransform.set(previousTransform)
    IconLoader.clearCacheInTests()
    IconLoader.deactivate()
    previousIconManager?.let { IconManager.activate(it) }
    previousRendererManager?.let { IconRendererManager.activate(it) }
  }

  @Test
  fun `native and bridged icons take the theme palette like the legacy icon`() = runInEdtAndWait {
    val plain = paint(native(PALETTE_ICON))
    SVGLoader.colorPatcherProvider = PALETTE

    val legacy = paint(legacy(PALETTE_ICON))
    assertEquals(RED, legacy.left, "guard: the palette must recolor the legacy icon")
    assertPixelsEqual(legacy, paint(native(PALETTE_ICON)), "native")
    assertPixelsEqual(legacy, paint(bridged(legacy(PALETTE_ICON))), "bridged")
    assertTrue(!plain.pixels.contentEquals(legacy.pixels), "guard: the palette must change the pixels")
  }

  @Test
  fun `a painted icon follows a palette change`() = runInEdtAndWait {
    val icon = native(PALETTE_ICON)
    assertEquals(GRAY, paint(icon).left)

    SVGLoader.colorPatcherProvider = PALETTE
    val themed = paint(icon)
    assertEquals(RED, themed.left)
    assertPixelsEqual(paint(legacy(PALETTE_ICON)), themed, "after the palette is installed")

    SVGLoader.colorPatcherProvider = null
    assertEquals(GRAY, paint(icon).left)
  }

  @Test
  fun `an svg patch runs on top of the theme palette`() = runInEdtAndWait {
    SVGLoader.colorPatcherProvider = PALETTE
    val patch = IconModifier.patchSvg(svgPatcher { replaceIfMatches("fill", "#ced0d6", "#0000ff") })

    val native = paint(native(PALETTE_ICON, patch))
    assertEquals(RED, native.left, "native: the palette")
    assertEquals(BLUE, native.right, "native: the own patch")

    val bridged = paint(bridged(legacy(PALETTE_ICON), patch))
    assertEquals(RED, bridged.left, "bridged: the palette")
    assertEquals(BLUE, bridged.right, "bridged: the own patch")
    assertPixelsEqual(native, bridged, "bridged")
  }

  @Test
  fun `a stroked icon does not take the theme palette, as legacy toStrokeIcon`() = runInEdtAndWait {
    val stroke = IconModifier.stroke(DefaultSRGB.fromHex("#FF00FF"))
    val unthemed = paint(native(PALETTE_ICON, stroke))
    assertEquals(MAGENTA, unthemed.left, "guard: the stroke must recolor the icon")

    SVGLoader.colorPatcherProvider = PALETTE
    assertPixelsEqual(unthemed, paint(native(PALETTE_ICON, stroke)), "native")
  }

  @Test
  fun `native and bridged icons take the global filter like the legacy icon`() = runInEdtAndWait {
    val plain = paint(legacy(PALETTE_ICON))
    IconLoader.setFilter(RotateChannelsFilter())

    val legacy = paint(legacy(PALETTE_ICON))
    assertTrue(!plain.pixels.contentEquals(legacy.pixels), "guard: the filter must change the pixels")
    assertPixelsEqual(legacy, paint(native(PALETTE_ICON)), "native")
    // The filter is not idempotent, so a bridged icon that applies it a second time does not match.
    assertPixelsEqual(legacy, paint(bridged(legacy(PALETTE_ICON))), "bridged")
  }

  @Test
  fun `the theme palette and the global filter combine like in the legacy icon`() = runInEdtAndWait {
    SVGLoader.colorPatcherProvider = PALETTE
    IconLoader.setFilter(RotateChannelsFilter())

    val legacy = paint(legacy(PALETTE_ICON))
    assertEquals(0xFF00FF00.toInt(), legacy.left, "guard: red rotated to green")
    assertPixelsEqual(legacy, paint(native(PALETTE_ICON)), "native")
    assertPixelsEqual(legacy, paint(bridged(legacy(PALETTE_ICON))), "bridged")
  }

  @Test
  fun `a file path patch target loads like the legacy icon`(@TempDir dir: Path) = runInEdtAndWait {
    val file = dir.resolve("patched.svg")
    file.writeText("""<svg width="16" height="16" viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg"><rect width="16" height="16" fill="#0000ff"/></svg>""")
    withPathPatch(file.toUri().toString()) {
      val legacy = paint(legacy(PALETTE_ICON))
      assertEquals(BLUE, legacy.left, "guard: the legacy icon must load the file")
      assertPixelsEqual(legacy, paint(native(PALETTE_ICON)), "native")
    }
  }

  @Test
  fun `a reflective path patch target loads like the legacy icon`() = runInEdtAndWait {
    withPathPatch(REFLECTIVE_TARGET) {
      val legacyIcon = IconLoader.findIcon(PALETTE_ICON, classLoader)
      assertSame(ThemeParityTestIcons.Target, legacyIcon, "guard: legacy resolves $REFLECTIVE_TARGET")
      val legacy = paint(legacyIcon!!)
      assertEquals(BLUE, legacy.left, "guard: the legacy icon is the target artwork")
      assertPixelsEqual(legacy, paint(native(PALETTE_ICON)), "native")
    }
  }

  @Test
  fun `the rendering context keeps the modifiers of the caller`() {
    val flow = IconRendererManager.createUpdateFlow(null)
    val modifiers = DefaultImageModifiers(isDark = !StartupUiUtil.isDarkTheme, sizeVariantPolicy = SizeVariantPolicy.None)
    assertEquals(modifiers, IconRendererManager.createRenderingContext(flow, modifiers).defaultImageModifiers)

    val defaults = IconRendererManager.createRenderingContext(flow).defaultImageModifiers as DefaultImageModifiers
    assertEquals(StartupUiUtil.isDarkTheme, defaults.isDark)
    assertEquals(SizeVariantPolicy.Exact, defaults.sizeVariantPolicy)
  }

  private fun withPathPatch(target: String, action: () -> Unit) {
    val patcher = object : IconPathPatcher() {
      override fun patchPath(path: String, classLoader: ClassLoader?): String? = if (path == "/$PALETTE_ICON") target else null
    }
    IconLoader.installPathPatcher(patcher)
    try {
      action()
    }
    finally {
      IconLoader.removePathPatcher(patcher)
    }
  }

  private fun native(path: String, modifier: IconModifier = IconModifier): SwingIcon =
    imageIconDescriptor(path, classLoader, modifier).createSwingIcon() as SwingIcon

  private fun legacy(path: String): Icon = IconLoader.findIcon(path, classLoader)!!

  private fun bridged(legacy: Icon, modifier: IconModifier = IconModifier): SwingIcon =
    SwingIcon(DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, modifier))))

  private class Painted(val width: Int, val height: Int, val pixels: IntArray) {
    val left: Int get() = pixels[(height / 2) * width + width / 4]
    val right: Int get() = pixels[(height / 2) * width + width * 3 / 4]
  }

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
    return Painted(width, height, image.getRGB(0, 0, width, height, null, 0, width))
  }

  private fun assertPixelsEqual(expected: Painted, actual: Painted, label: String) {
    assertEquals(expected.width to expected.height, actual.width to actual.height, "$label: size")
    val differences = expected.pixels.indices.count { expected.pixels[it] != actual.pixels[it] }
    assertEquals(0, differences, "$label: pixels different from the legacy icon " +
                                 "(left #${Integer.toHexString(actual.left)}, expected #${Integer.toHexString(expected.left)})")
  }

  /** Moves red to green, green to blue and blue to red. Applied twice, it gives a different image than once. */
  private class RotateChannelsFilter : RGBImageFilter() {
    override fun filterRGB(x: Int, y: Int, rgb: Int): Int {
      val r = rgb shr 16 and 0xff
      val g = rgb shr 8 and 0xff
      val b = rgb and 0xff
      return (rgb and -0x1000000) or (b shl 16) or (r shl 8) or g
    }
  }

  private companion object {
    const val PALETTE_ICON = "iconsTest/theme/palette.svg"
    const val REFLECTIVE_TARGET = "com.intellij.platform.icons.impl.intellij.ThemeParityTestIcons.Target"
    const val GRAY = 0xFF6C707E.toInt()
    const val RED = 0xFFFF0000.toInt()
    const val BLUE = 0xFF0000FF.toInt()
    const val MAGENTA = 0xFFFF00FF.toInt()

    val PALETTE = object : SVGLoader.SvgElementColorPatcherProvider {
      private val patcher = newSvgPatcher(mapOf("#6c707e" to "#ff0000"), alphaProvider = { null })
      override fun attributeForPath(path: String) = patcher
      override fun digest(): LongArray = longArrayOf(42, 4242)
    }
  }
}

/** The holder of a reflective path patch target, as a generated `*Icons` class is. */
object ThemeParityTestIcons {
  @JvmField
  val Target: Icon = IconLoader.getIcon("iconsTest/theme/target.svg", ThemeParityTestIcons::class.java.classLoader)
}
