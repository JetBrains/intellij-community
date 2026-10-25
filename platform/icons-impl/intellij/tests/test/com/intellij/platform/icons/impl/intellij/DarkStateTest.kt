// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.impl.modifiers.DarkModifier
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.impl.rendering.layers.IconLayerManager
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.modifiers.dark
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.modifiers.sizeVariants
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceProvider
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.platform.icons.swing.swingIcon
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.getMenuBarIcon
import com.intellij.ui.icons.pathTransform
import com.intellij.ui.scale.JBUIScale
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import javax.swing.Icon

/**
 * The dark state in the new icons API: it follows the theme (`IconLoader.setUseDarkIcons`) at paint time, the
 * `IconModifier.dark` modifier overrides it, and `IconLoader.getDarkIcon` works for a [SwingIcon] as for a legacy icon.
 *
 * Test artwork (iconsTest/variant): `test.svg` 16×16 with a red square (`_dark`: green); `test@20x20.svg` with a blue
 * square (`_dark`: yellow).
 */
@TestApplication
class DarkStateTest {
  private var previousIconManager: IconManager? = null
  private var previousRendererManager: IconRendererManager? = null
  private var previousLayerManager: IconLayerManager? = null
  private val previousTransform = pathTransform.get()
  private val classLoader: ClassLoader = DarkStateTest::class.java.classLoader

  @BeforeEach
  fun setUp() {
    previousIconManager = runCatching { IconManager.getInstance() }.getOrNull()
    previousRendererManager = runCatching { IconRendererManager.getInstance() }.getOrNull()
    previousLayerManager = runCatching { IconLayerManager.getInstance() }.getOrNull()
    IntelliJIconManager.activate()
    IconLoader.activate()
    IconLoader.setUseDarkIcons(false)
    IconLoader.clearCacheInTests()
  }

  @AfterEach
  fun tearDown() {
    pathTransform.set(previousTransform)
    IconLoader.clearCacheInTests()
    IconLoader.deactivate()
    previousIconManager?.let { IconManager.activate(it) }
    previousRendererManager?.let { IconRendererManager.activate(it) }
    previousLayerManager?.let { IconLayerManager.setInstance(it) }
  }

  // ---- the theme ----

  @Test
  fun `a painted SwingIcon follows a dark switch like the legacy icon`() = runInEdtAndWait {
    val icon = native(TEST)
    val light = paint(icon)
    assertEquals(RED, light.center)
    assertPixelsEqual(paint(legacy(TEST)), light, "light")

    IconLoader.setUseDarkIcons(true)
    val dark = paint(icon)
    assertEquals(GREEN, dark.center)
    assertPixelsEqual(paint(legacy(TEST)), dark, "dark")

    IconLoader.setUseDarkIcons(false)
    val lightAgain = paint(icon)
    assertEquals(RED, lightAgain.center)
    assertPixelsEqual(paint(legacy(TEST)), lightAgain, "light again")
  }

  @Test
  fun `a bridged legacy icon follows a dark switch`() = runInEdtAndWait {
    val icon = bridged(legacy(TEST))
    assertEquals(RED, paint(icon).center)
    IconLoader.setUseDarkIcons(true)
    assertEquals(GREEN, paint(icon).center)
  }

  @Test
  fun `a dark switch changes the theme digest and rebuilds the renderers once`() = runInEdtAndWait {
    val base = IconRendererManager.createRenderingContext(IconRendererManager.createUpdateFlow(null)) as DefaultRenderingContext
    val provider = CountingImageResourceProvider()
    val context = DefaultRenderingContext(base.updateFlow, base.defaultImageModifiers, base.theme, provider)
    val run = IconTestRun(IconRendererManager.getInstance().createRenderer(imageIconDescriptor(TEST, classLoader), context))
    assertEquals(listOf(false), provider.darkStates, "the first build")

    repeat(3) { run.performRender() }
    assertEquals(listOf(false), provider.darkStates, "paints without a dark switch")

    val digest = base.theme.digest()
    IconLoader.setUseDarkIcons(true)
    assertNotEquals(digest, base.theme.digest(), "the dark switch must change the theme digest")
    repeat(3) { run.performRender() }
    assertEquals(listOf(false, true), provider.darkStates, "paints after the dark switch")

    IconLoader.setUseDarkIcons(false)
    repeat(3) { run.performRender() }
    assertEquals(listOf(false, true, false), provider.darkStates, "paints after the switch back")
  }

  // ---- the dark modifier ----

  @Test
  fun `the dark modifier wins over the theme`() = runInEdtAndWait {
    assertEquals(GREEN, paint(native(TEST, IconModifier.dark(true))).center)
    assertEquals(RED, paint(native(TEST, IconModifier.dark(false))).center)

    IconLoader.setUseDarkIcons(true)
    assertEquals(GREEN, paint(native(TEST, IconModifier.dark(true))).center)
    assertEquals(RED, paint(native(TEST, IconModifier.dark(false))).center)
  }

  @Test
  fun `the dark modifier matches the legacy dark icon`() = runInEdtAndWait {
    assertPixelsEqual(paint(IconLoader.getDarkIcon(legacy(TEST), true)), paint(native(TEST, IconModifier.dark(true))), "dark")
    IconLoader.setUseDarkIcons(true)
    assertPixelsEqual(paint(IconLoader.getDarkIcon(legacy(TEST), false)), paint(native(TEST, IconModifier.dark(false))), "light")
  }

  @Test
  fun `the dark modifier on an outer nested layer applies to the inner image layer`() = runInEdtAndWait {
    val inner = imageIconDescriptor(TEST, classLoader)
    assertEquals(GREEN, paint(iconDescriptor { icon(inner, IconModifier.dark(true)) }.createSwingIcon() as Icon).center)
    IconLoader.setUseDarkIcons(true)
    assertEquals(RED, paint(iconDescriptor { icon(inner, IconModifier.dark(false)) }.createSwingIcon() as Icon).center)
  }

  @Test
  fun `the innermost dark modifier wins`() = runInEdtAndWait {
    val inner = imageIconDescriptor(TEST, classLoader, IconModifier.dark(false))
    assertEquals(RED, paint(iconDescriptor { icon(inner, IconModifier.dark(true)) }.createSwingIcon() as Icon).center)
    val innerDark = imageIconDescriptor(TEST, classLoader, IconModifier.dark(true))
    val nested = DefaultLayeredIconDescriptor(listOf(NestedIconLayer(innerDark, IconModifier.dark(false))))
    assertEquals(GREEN, paint(nested.createSwingIcon() as Icon).center)
  }

  @Test
  fun `the dark modifier on a layout layer applies to its children`() = runInEdtAndWait {
    val row = iconDescriptor { row(IconModifier.dark(true)) { image(TEST, classLoader) } }
    assertEquals(GREEN, paint(row.createSwingIcon() as Icon).center)
    val box = iconDescriptor { box(IconModifier.dark(true)) { image(TEST, classLoader) } }
    assertEquals(GREEN, paint(box.createSwingIcon() as Icon).center)
  }

  @Test
  fun `the dark modifier on an animation layer applies to its frames`() = runInEdtAndWait {
    val descriptor = iconDescriptor {
      animation(IconModifier.dark(true)) {
        frame(1_000_000) { image(TEST, classLoader) }
      }
    }
    assertEquals(GREEN, paint(descriptor.createSwingIcon() as Icon).center)
  }

  @Test
  fun `the dark modifier combines with size variants`() = runInEdtAndWait {
    val icon = native(TEST, IconModifier.dark(true)).scaled(factor(1.25f)) as Icon
    assertEquals(YELLOW, paint(icon).center, "test@20x20_dark.svg")
    val none = native(TEST, IconModifier.dark(true).sizeVariants(SizeVariantPolicy.None)).scaled(factor(1.25f)) as Icon
    assertEquals(GREEN, paint(none).center, "test_dark.svg, scaled")
    val outer = iconDescriptor { icon(imageIconDescriptor(TEST, classLoader), IconModifier.dark(true).scale(factor(1.25f))) }
    assertEquals(YELLOW, paint(outer.createSwingIcon() as Icon).center, "inherited, test@20x20_dark.svg")
  }

  @Test
  fun `the dark modifier reaches bridged swing layers, own or inherited`() = runInEdtAndWait {
    val legacy = legacy(TEST)
    val own = DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, IconModifier.dark(true))))
    assertEquals(GREEN, paint(SwingIcon(own)).center)
    val plain = DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, IconModifier)))
    val inherited = DefaultLayeredIconDescriptor(listOf(NestedIconLayer(plain, IconModifier.dark(true))))
    assertEquals(GREEN, paint(SwingIcon(inherited)).center)
    assertPixelsEqual(paint(IconLoader.getDarkIcon(legacy, true)), paint(SwingIcon(inherited)), "bridged")
  }

  @Test
  fun `modifier serialization round trip`() {
    for (dark in listOf(true, false)) {
      val modifier = DarkModifier(dark)
      val json = Json.encodeToString(DarkModifier.serializer(), modifier)
      assertEquals(modifier, Json.decodeFromString(DarkModifier.serializer(), json), json)
    }
  }

  // ---- IconLoader.getDarkIcon ----

  @Test
  fun `getDarkIcon of a SwingIcon gives the pixels of the legacy dark icon`() = runInEdtAndWait {
    val icon = native(TEST)
    assertPixelsEqual(paint(IconLoader.getDarkIcon(legacy(TEST), true)), paint(IconLoader.getDarkIcon(icon, true)), "dark")
    assertEquals(GREEN, paint(IconLoader.getDarkIcon(icon, true)).center)

    IconLoader.setUseDarkIcons(true)
    assertPixelsEqual(paint(IconLoader.getDarkIcon(legacy(TEST), false)), paint(IconLoader.getDarkIcon(icon, false)), "light")
    assertEquals(RED, paint(IconLoader.getDarkIcon(icon, false)).center)
    assertEquals(GREEN, paint(icon).center, "the original follows the theme")
  }

  @Test
  fun `getDarkIcon of a SwingIcon is memoized`() {
    val icon = native(TEST)
    val dark = IconLoader.getDarkIcon(icon, true) as SwingIcon
    val light = IconLoader.getDarkIcon(icon, false) as SwingIcon
    assertSame(dark, IconLoader.getDarkIcon(icon, true))
    assertSame(light, IconLoader.getDarkIcon(icon, false))
    assertSame(dark, IconLoader.getDarkIcon(dark, true))
    assertSame(light, IconLoader.getDarkIcon(dark, false))
    assertSame(dark, IconLoader.getDarkIcon(light, true))
    assertNotSame(icon, dark)
    assertEquals(true, dark.dark)
    assertEquals(false, light.dark)
    assertSame(icon.iconDescriptor, dark.iconDescriptor)
  }

  @Test
  fun `scaling keeps the dark state of getDarkIcon`() = runInEdtAndWait {
    val dark = IconLoader.getDarkIcon(native(TEST), true) as SwingIcon
    val scaled = dark.scale(1.25f) as SwingIcon
    assertEquals(true, scaled.dark)
    assertEquals(YELLOW, paint(scaled).center, "test@20x20_dark.svg")
    assertSame(scaled, dark.scale(1.25f))
    assertEquals(true, (dark.scaled(factor(1.25f)) as SwingIcon).dark)
    assertEquals(YELLOW, paint(dark.scaled(factor(1.25f)) as Icon).center)

    val scaledFirst = native(TEST).scale(1.25f)
    val darkScaled = IconLoader.getDarkIcon(scaledFirst, true) as SwingIcon
    assertEquals(1.25f, darkScaled.getScale())
    assertEquals(YELLOW, paint(darkScaled).center)
  }

  @Test
  fun `the menu bar icon of a SwingIcon matches the legacy menu bar icon`() = runInEdtAndWait {
    val icon = native(TEST)
    val legacy = legacy(TEST) as CachedImageIcon
    for (dark in listOf(true, false)) {
      val expected = legacy.getMenuBarIcon(dark)
      val actual = getMenuBarIcon(icon, dark)
      assertEquals(expected.iconWidth to expected.iconHeight, actual.iconWidth to actual.iconHeight, "dark $dark: size")
      for (density in listOf(1.0, 2.0)) {
        assertPixelsEqual(paint(expected, density), paint(actual, density), "dark $dark, density $density")
      }
      assertEquals(if (dark) GREEN else RED, paint(actual).center, "dark $dark")
    }
    assertPixelsEqual(paint(legacy.getMenuBarIcon(true)), paint(getMenuBarIcon(icon.scale(1.25f), true)),
                      "the scale of the icon is not used, as in the legacy icon")
  }

  /**
   * With an IDE zoom, the menu bar icon has the size of the user scale 1 and the right artwork. It is not pixel-identical
   * to the legacy one: the new pipeline rasterizes a fractional object scale differently from the legacy icons.
   */
  @Test
  fun `the menu bar icon of a SwingIcon ignores the IDE zoom`() = runInEdtAndWait {
    val previousZoom = JBUIScale.scale(1f)
    try {
      JBUIScale.setUserScaleFactorForTest(1.25f)
      IconLoader.clearCacheInTests()
      val icon = native(TEST)
      assertEquals(20, icon.iconWidth, "guard: the zoom must apply")
      for (dark in listOf(true, false)) {
        val expected = (legacy(TEST) as CachedImageIcon).getMenuBarIcon(dark)
        val actual = getMenuBarIcon(icon, dark)
        assertEquals(expected.iconWidth to expected.iconHeight, actual.iconWidth to actual.iconHeight, "dark $dark: size")
        assertEquals(if (dark) GREEN else RED, paint(actual).center, "dark $dark")
      }
    }
    finally {
      JBUIScale.setUserScaleFactorForTest(previousZoom)
    }
  }

  @Test
  fun `a SwingIcon with a dark state converts to a descriptor that keeps it`() = runInEdtAndWait {
    val icon = native(TEST)
    val manager = IconManager.getInstance()
    assertSame(icon.iconDescriptor, manager.toIconDescriptor(icon), "the identity case unwraps")

    val dark = IconLoader.getDarkIcon(icon, true)
    val converted = manager.toIconDescriptor(dark)
    assertEquals(GREEN, paint(converted.createSwingIcon() as Icon).center)
    val scaledDark = manager.toIconDescriptor((dark as SwingIcon).scale(1.25f))
    assertEquals(YELLOW, paint(scaledDark.createSwingIcon() as Icon).center)

    val designed = iconDescriptor { swingIcon(dark) }
    assertEquals(GREEN, paint(designed.createSwingIcon() as Icon).center, "a SwingIcon added to a designer")
  }

  /**
   * The main toolbar writes `getDarkIcon(presentation.icon)` back into the presentation on each update, and the
   * presentation converts it to a descriptor and back. The descriptor must stay the same and must not nest deeper.
   */
  @Test
  fun `a repeated getDarkIcon round trip through a descriptor is stable`() = runInEdtAndWait {
    val manager = IconManager.getInstance()
    val first = manager.toIconDescriptor(IconLoader.getDarkIcon(native(TEST), true))
    var descriptor = first
    repeat(5) {
      descriptor = manager.toIconDescriptor(IconLoader.getDarkIcon(descriptor.createSwingIcon() as Icon, true))
    }
    assertEquals(first, descriptor)
    val layer = (descriptor as DefaultLayeredIconDescriptor).layers.single() as NestedIconLayer
    assertEquals(imageIconDescriptor(TEST, classLoader), layer.iconDescriptor, "one level of nesting")
    assertEquals(GREEN, paint(descriptor.createSwingIcon() as Icon).center)

    val light = manager.toIconDescriptor(IconLoader.getDarkIcon(descriptor.createSwingIcon() as Icon, false))
    assertEquals(imageIconDescriptor(TEST, classLoader), ((light as DefaultLayeredIconDescriptor).layers.single() as NestedIconLayer).iconDescriptor,
                 "the light state replaces the dark state")
    IconLoader.setUseDarkIcons(true)
    assertEquals(RED, paint(light.createSwingIcon() as Icon).center)
    assertEquals(RED, paint(IconLoader.getDarkIcon(descriptor.createSwingIcon() as Icon, false)).center)
  }

  @Test
  fun `the rendering context keeps the dark override of the caller`() {
    val flow = IconRendererManager.createUpdateFlow(null)
    val modifiers = DefaultImageModifiers(darkOverride = true)
    val context = IconRendererManager.createRenderingContext(flow, modifiers)
    assertEquals(modifiers, context.defaultImageModifiers)
    assertEquals(true, (context.defaultImageModifiers as DefaultImageModifiers).isDark)
  }

  private fun native(path: String, modifier: IconModifier = IconModifier): SwingIcon =
    imageIconDescriptor(path, classLoader, modifier).createSwingIcon() as SwingIcon

  private fun legacy(path: String): Icon = IconLoader.findIcon(path, classLoader)!!

  private fun bridged(legacy: Icon): SwingIcon = SwingIcon(DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, IconModifier))))

  private class Painted(val width: Int, val height: Int, val pixels: IntArray) {
    val center: Int get() = pixels[(height / 2) * width + width / 2]
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
                                 "(center #${Integer.toHexString(actual.center)}, expected #${Integer.toHexString(expected.center)})")
  }

  /** Records the effective dark state of each image load. */
  private class CountingImageResourceProvider : ImageResourceProvider {
    val darkStates = ArrayList<Boolean>()

    override fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?): ImageResource {
      darkStates.add((imageModifiers as DefaultImageModifiers).isDark)
      return TestImageResource(TestImageResourceLocation(16, 16))
    }
  }

  private companion object {
    const val TEST = "iconsTest/variant/test.svg"
    const val RED = 0xFFFF0000.toInt()
    const val GREEN = 0xFF00FF00.toInt()
    const val YELLOW = 0xFFFFFF00.toInt()
  }
}
