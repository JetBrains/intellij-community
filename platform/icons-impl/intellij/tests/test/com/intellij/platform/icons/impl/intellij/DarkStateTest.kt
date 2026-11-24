// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.DefaultIconManager
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.impl.modifiers.ForceDarkVariantModifier
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.impl.rendering.layers.IconLayerManager
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.modifiers.forceDarkVariant
import com.intellij.platform.icons.modifiers.forceLightVariant
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.modifiers.sizeVariants
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceProvider
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.scale.fitArea
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.platform.icons.swing.swingIcon
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.getMenuBarIcon
import com.intellij.ui.icons.pathTransform
import com.intellij.ui.scale.JBUIScale
import java.awt.image.BufferedImage
import javax.swing.Icon
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The dark state in the new icons API: it follows the theme (`IconLoader.setUseDarkIcons`) at paint time, the
 * `IconModifier.forceDarkVariant` and `IconModifier.forceLightVariant` modifiers override it, and `IconLoader.getDarkIcon` works for a [SwingIcon] as for a legacy icon.
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
    assertEquals(GREEN, paint(native(TEST, IconModifier.forceDarkVariant())).center)
    assertEquals(RED, paint(native(TEST, IconModifier.forceLightVariant())).center)

    IconLoader.setUseDarkIcons(true)
    assertEquals(GREEN, paint(native(TEST, IconModifier.forceDarkVariant())).center)
    assertEquals(RED, paint(native(TEST, IconModifier.forceLightVariant())).center)
  }

  @Test
  fun `the dark modifier matches the legacy dark icon`() = runInEdtAndWait {
    assertPixelsEqual(paint(IconLoader.getDarkIcon(legacy(TEST), true)), paint(native(TEST, IconModifier.forceDarkVariant())), "dark")
    IconLoader.setUseDarkIcons(true)
    assertPixelsEqual(paint(IconLoader.getDarkIcon(legacy(TEST), false)), paint(native(TEST, IconModifier.forceLightVariant())), "light")
  }

  @Test
  fun `the dark modifier on an outer nested layer applies to the inner image layer`() = runInEdtAndWait {
    val inner = imageIconDescriptor(TEST, classLoader)
    assertEquals(GREEN, paint(iconDescriptor { icon(inner, IconModifier.forceDarkVariant()) }.createSwingIcon() as Icon).center)
    IconLoader.setUseDarkIcons(true)
    assertEquals(RED, paint(iconDescriptor { icon(inner, IconModifier.forceLightVariant()) }.createSwingIcon() as Icon).center)
  }

  @Test
  fun `the innermost dark modifier wins`() = runInEdtAndWait {
    val inner = imageIconDescriptor(TEST, classLoader, IconModifier.forceLightVariant())
    assertEquals(RED, paint(iconDescriptor { icon(inner, IconModifier.forceDarkVariant()) }.createSwingIcon() as Icon).center)
    val innerDark = imageIconDescriptor(TEST, classLoader, IconModifier.forceDarkVariant())
    val nested = DefaultLayeredIconDescriptor(listOf(NestedIconLayer(innerDark, IconModifier.forceLightVariant())))
    assertEquals(GREEN, paint(nested.createSwingIcon() as Icon).center)
  }

  @Test
  fun `the dark modifier on a layout layer applies to its children`() = runInEdtAndWait {
    val row = iconDescriptor { row(IconModifier.forceDarkVariant()) { image(TEST, classLoader) } }
    assertEquals(GREEN, paint(row.createSwingIcon() as Icon).center)
    val box = iconDescriptor { box(IconModifier.forceDarkVariant()) { image(TEST, classLoader) } }
    assertEquals(GREEN, paint(box.createSwingIcon() as Icon).center)
  }

  @Test
  fun `the dark modifier on an animation layer applies to its frames`() = runInEdtAndWait {
    val descriptor = iconDescriptor {
      animation(IconModifier.forceDarkVariant()) {
        frame(1_000_000) { image(TEST, classLoader) }
      }
    }
    assertEquals(GREEN, paint(descriptor.createSwingIcon() as Icon).center)
  }

  @Test
  fun `the dark modifier combines with size variants`() = runInEdtAndWait {
    val icon = native(TEST, IconModifier.forceDarkVariant()).scaled(factor(1.25f)) as Icon
    assertEquals(YELLOW, paint(icon).center, "test@20x20_dark.svg")
    val none = native(TEST, IconModifier.forceDarkVariant().sizeVariants(SizeVariantPolicy.None)).scaled(factor(1.25f)) as Icon
    assertEquals(GREEN, paint(none).center, "test_dark.svg, scaled")
    val outer = iconDescriptor { icon(imageIconDescriptor(TEST, classLoader), IconModifier.forceDarkVariant().scale(factor(1.25f))) }
    assertEquals(YELLOW, paint(outer.createSwingIcon() as Icon).center, "inherited, test@20x20_dark.svg")
  }

  @Test
  fun `the dark modifier reaches bridged swing layers, own or inherited`() = runInEdtAndWait {
    val legacy = legacy(TEST)
    val own = DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, IconModifier.forceDarkVariant())))
    assertEquals(GREEN, paint(SwingIcon(own)).center)
    val plain = DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, IconModifier)))
    val inherited = DefaultLayeredIconDescriptor(listOf(NestedIconLayer(plain, IconModifier.forceDarkVariant())))
    assertEquals(GREEN, paint(SwingIcon(inherited)).center)
    assertPixelsEqual(paint(IconLoader.getDarkIcon(legacy, true)), paint(SwingIcon(inherited)), "bridged")
  }

  @Test
  fun `modifier serialization round trip`() {
    for (dark in listOf(true, false)) {
      val modifier = ForceDarkVariantModifier(dark)
      val json = Json.encodeToString(ForceDarkVariantModifier.serializer(), modifier)
      assertEquals(modifier, Json.decodeFromString(ForceDarkVariantModifier.serializer(), json), json)
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
   * With an IDE zoom, the menu bar icon has the size of the user scale 1 and the pixels of the legacy menu bar icon. The
   * icon renders once at the user scale 1, so the zoom does not change its rasterization.
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
        for (density in listOf(1.0, 2.0)) {
          assertPixelsEqual(paint(expected, density), paint(actual, density), "dark $dark, density $density")
        }
        assertEquals(if (dark) GREEN else RED, paint(actual).center, "dark $dark")
      }
    }
    finally {
      JBUIScale.setUserScaleFactorForTest(previousZoom)
    }
  }

  /** A bridged legacy icon of a fixed size keeps its size in the menu bar at each zoom, as the legacy menu bar icon does. */
  @Test
  fun `the menu bar icon of a bridged legacy icon of a fixed size keeps its size`() = runInEdtAndWait {
    val icon = bridged(javax.swing.ImageIcon(BufferedImage(16, 12, BufferedImage.TYPE_INT_ARGB)))
    for (zoom in listOf(1f, 2f)) {
      val previousZoom = JBUIScale.scale(1f)
      try {
        JBUIScale.setUserScaleFactorForTest(zoom)
        assertEquals(16 to 12, icon.iconWidth to icon.iconHeight, "zoom $zoom: the icon")
        val menuBar = getMenuBarIcon(icon, false)
        assertEquals(16 to 12, menuBar.iconWidth to menuBar.iconHeight, "zoom $zoom: the menu bar icon")
        assertEquals(16 to 12, icon.iconWidth to icon.iconHeight, "zoom $zoom: the icon after the menu bar icon")
      }
      finally {
        JBUIScale.setUserScaleFactorForTest(previousZoom)
      }
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

  /**
   * A scaled dark icon converts to a descriptor whose nested layer has the scale and the dark state in one modifier.
   * `getDarkIcon(false)` must still replace the dark state, and the round trip must not nest deeper.
   */
  @Test
  fun `a scaled dark round trip through a descriptor can go back to light`() = runInEdtAndWait {
    val manager = IconManager.getInstance()
    IconLoader.setUseDarkIcons(true)
    // 1.5 has no size variant, so the base artwork is drawn: green when dark, red when light
    val scaledDark = (IconLoader.getDarkIcon(native(TEST), true) as SwingIcon).scale(1.5f)
    val descriptor = manager.toIconDescriptor(scaledDark)
    assertEquals(GREEN, paint(descriptor.createSwingIcon() as Icon).center)

    val light = IconLoader.getDarkIcon(descriptor.createSwingIcon() as Icon, false)
    assertEquals(RED, paint(light).center)
    assertEquals(RED, paint(manager.toIconDescriptor(light).createSwingIcon() as Icon).center)

    val again = manager.toIconDescriptor(IconLoader.getDarkIcon(descriptor.createSwingIcon() as Icon, true))
    assertEquals(again, manager.toIconDescriptor(IconLoader.getDarkIcon(again.createSwingIcon() as Icon, true)), "no deeper nesting")
    assertEquals(GREEN, paint(again.createSwingIcon() as Icon).center)
  }

  /** A bridged legacy icon with an explicit dark state measures the artwork that it paints, here of another size. */
  @Test
  fun `a bridged legacy icon measures the dark artwork of a forced dark state`() = runInEdtAndWait {
    val icon = bridged(legacy(DARK_SIZE))
    assertEquals(16 to 16, icon.iconWidth to icon.iconHeight, "the light artwork")

    val dark = IconLoader.getDarkIcon(icon, true)
    assertEquals(24 to 14, dark.iconWidth to dark.iconHeight, "the dark artwork")
    assertEquals(GREEN, paint(dark).center)
    val stroked = SwingIcon(iconDescriptor { swingIcon(dark) }).createStrokeIcon(java.awt.Color.RED)
    assertEquals(16 to 16, stroked.iconWidth to stroked.iconHeight, "a stroked icon has the light artwork")
  }

  /**
   * `getDarkIcon` changes only the dark state of the icon. A dark state that the descriptor forces inside stays and wins,
   * also after a scale and a toggle back, when the icon paints and in its descriptor.
   */
  @Test
  fun `getDarkIcon keeps a dark state that the descriptor forces inside`() = runInEdtAndWait {
    val manager = IconManager.getInstance()
    val inner = iconDescriptor { icon(imageIconDescriptor(TEST, classLoader), IconModifier.forceDarkVariant()) }
    val icon = SwingIcon(iconDescriptor { icon(inner, IconModifier.forceLightVariant()) })
    assertEquals(GREEN, paint(icon).center, "the inner dark state wins")

    val chains = listOf(
      "getDarkIcon(false)" to IconLoader.getDarkIcon(icon, false),
      "getDarkIcon(true)" to IconLoader.getDarkIcon(icon, true),
      "scale, getDarkIcon(false)" to IconLoader.getDarkIcon(icon.scale(1.5f), false),
      "scale, getDarkIcon(true), getDarkIcon(false)" to IconLoader.getDarkIcon(IconLoader.getDarkIcon(icon.scale(1.5f), true), false),
      "getDarkIcon(false), scale, getDarkIcon(true)" to IconLoader.getDarkIcon((IconLoader.getDarkIcon(icon, false) as SwingIcon).scale(1.5f), true),
      "getDarkIcon(true), scale, getDarkIcon(false)" to IconLoader.getDarkIcon((IconLoader.getDarkIcon(icon, true) as SwingIcon).scale(1.5f), false),
      "getDarkIcon(false), scale, getDarkIcon(true), getDarkIcon(false)" to
        IconLoader.getDarkIcon(IconLoader.getDarkIcon((IconLoader.getDarkIcon(icon, false) as SwingIcon).scale(1.5f), true), false),
    )
    for ((label, variant) in chains) {
      assertEquals(GREEN, paint(variant).center, label)
      assertEquals(GREEN, paint(manager.toIconDescriptor(variant).createSwingIcon() as Icon).center, "$label, as a descriptor")
    }
  }

  /** A scaled dark icon gives the same icon and the same descriptor at each depth of a round trip, also through JSON. */
  @Test
  fun `a scaled dark icon round trips through a descriptor and JSON`() = runInEdtAndWait {
    val manager = IconManager.getInstance()
    val json = Json { serializersModule = (manager as DefaultIconManager).getSerializersModule() }
    val serializer = PolymorphicSerializer(IconDescriptor::class)
    val icon = IconLoader.getDarkIcon(native(TEST).scale(1.5f), true) as SwingIcon
    val descriptor = manager.toIconDescriptor(icon)

    var current: Icon = icon
    repeat(3) { depth ->
      val text = json.encodeToString(serializer, manager.toIconDescriptor(current))
      current = json.decodeFromString(serializer, text).createSwingIcon() as Icon
      assertEquals(icon, current, "depth $depth: $text")
    }
    assertEquals(descriptor, manager.toIconDescriptor(current))
    assertEquals(GREEN, paint(current).center)
  }

  /** A limited fit with a factor, a stroke and a scale keep the dark state, and a dark toggle keeps them. */
  @Test
  fun `getDarkIcon keeps a limited fit and a stroke, and they keep the dark state`() = runInEdtAndWait {
    val fitted = native(TEST).scaled(fitArea(32.dp, 32.dp, upscale = false)).scaled(factor(2f)) as SwingIcon
    val dark = IconLoader.getDarkIcon(fitted, true) as SwingIcon
    assertEquals(32 to 32, dark.iconWidth to dark.iconHeight, "the fit and the factor stay")
    assertEquals(GREEN, paint(dark).center)
    assertSame(IconLoader.getDarkIcon(fitted, false), IconLoader.getDarkIcon(dark, false), "a toggle back gives the same icon")

    val stroked = dark.createStrokeIcon(java.awt.Color.RED) as SwingIcon
    assertEquals(true, stroked.dark, "a stroke keeps the dark state")
    assertEquals(dark.createStrokeIcon(java.awt.Color.BLUE), stroked.createStrokeIcon(java.awt.Color.BLUE), "a stroke replaces a stroke")
  }

  @Test
  fun `the rendering context keeps the dark override of the caller`() {
    val flow = IconRendererManager.createUpdateFlow(null)
    val modifiers = DefaultImageModifiers(forcedDark = true)
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
    const val DARK_SIZE = "iconsTest/variant/darkSize.svg"
    const val RED = 0xFFFF0000.toInt()
    const val GREEN = 0xFF00FF00.toInt()
    const val YELLOW = 0xFFFFFF00.toInt()
  }
}
