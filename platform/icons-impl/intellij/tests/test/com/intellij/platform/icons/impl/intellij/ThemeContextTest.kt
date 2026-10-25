// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.IntelliJThemeImageFilter
import com.intellij.platform.icons.impl.intellij.rendering.IntelliJThemeSvgPatcher
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.intellij.rendering.SwingLayerPaintingContext
import com.intellij.platform.icons.impl.intellij.rendering.images.IntelliJImageResourceProvider
import com.intellij.platform.icons.impl.intellij.rendering.intelliJColorPalette
import com.intellij.platform.icons.impl.intellij.rendering.intelliJGlobalFilter
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.impl.rendering.darkState
import com.intellij.platform.icons.impl.rendering.layers.generateImageModifiers
import com.intellij.platform.icons.impl.rendering.resolve
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceProvider
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.platform.icons.rendering.ThemeImageFilter
import com.intellij.platform.icons.rendering.ThemeSvgPatcher
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.icons.pathTransform
import com.intellij.ui.icons.pathTransformGlobalModCount
import com.intellij.ui.svg.getSelectionColorPatcherProvider
import com.intellij.ui.svg.newSvgPatcher
import com.intellij.ui.svg.setSelectionColorPatcherProvider
import com.intellij.util.SVGLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.awt.image.RGBImageFilter
import javax.swing.Icon
import kotlin.time.Duration.Companion.seconds

/**
 * The theme inputs of the new icons API (`ThemeContext`): the IDE theme reads them on each call, its digest changes
 * with each of them, a theme change rebuilds the renderers once, and the image loaders apply the inputs of the theme of
 * the rendering context, also of a custom theme.
 *
 * Test artwork (iconsTest/theme): `switch.svg` 16×16, the left half `#ced0d6`, the right half `#6c707e`;
 * `switch_dark.svg`, the left half `#6c707e`, the right half `#00ff00`.
 */
@TestApplication
class ThemeContextTest {
  private var previousIconManager: IconManager? = null
  private var previousRendererManager: IconRendererManager? = null
  private var previousPalette: SVGLoader.SvgElementColorPatcherProvider? = null
  private var previousSelectionPatcher: SVGLoader.SvgElementColorPatcherProvider? = null
  private val previousTransform = pathTransform.get()
  private val classLoader: ClassLoader = ThemeContextTest::class.java.classLoader

  @BeforeEach
  fun setUp() {
    previousIconManager = runCatching { IconManager.getInstance() }.getOrNull()
    previousRendererManager = runCatching { IconRendererManager.getInstance() }.getOrNull()
    previousPalette = SVGLoader.colorPatcherProvider
    previousSelectionPatcher = getSelectionColorPatcherProvider()
    IntelliJIconManager.activate()
    IconLoader.activate()
    SVGLoader.colorPatcherProvider = null
    IconLoader.setUseDarkIcons(false)
    IconLoader.clearCacheInTests()
  }

  @AfterEach
  fun tearDown() {
    SVGLoader.colorPatcherProvider = previousPalette
    setSelectionColorPatcherProvider(previousSelectionPatcher)
    pathTransform.set(previousTransform)
    IconLoader.clearCacheInTests()
    IconLoader.deactivate()
    previousIconManager?.let { IconManager.activate(it) }
    previousRendererManager?.let { IconRendererManager.activate(it) }
  }

  // ---- the IDE theme ----

  @Test
  fun `the IDE theme reads its inputs on each call`() {
    val theme = ideTheme()
    assertNull(theme.colorPalette())
    assertNull(theme.globalFilter())
    assertEquals(false, theme.isDark())

    SVGLoader.colorPatcherProvider = PALETTE
    assertSame(PALETTE, theme.intelliJColorPalette())
    assertEquals(theme.colorPalette(), IntelliJThemeSvgPatcher(PALETTE))
    assertEquals("42,4242", theme.colorPalette()!!.digest())

    val filter = RotateChannelsFilter()
    IconLoader.setFilter(filter)
    assertSame(filter, theme.intelliJGlobalFilter())

    IconLoader.setUseDarkIcons(true)
    assertEquals(true, theme.isDark())

    setSelectionColorPatcherProvider(PALETTE)
    assertSame(PALETTE, (theme.selectionPatcher() as IntelliJThemeSvgPatcher).provider)
    assertNotNull((theme.grayFilter() as IntelliJThemeImageFilter).filter)
  }

  @Test
  fun `the digest of the IDE theme changes with each theme input`() {
    val theme = ideTheme()
    val digests = ArrayList<String>()
    digests.add(theme.digest())
    assertEquals(digests.last(), theme.digest(), "the digest is stable without a change")

    val palette = MutableDigestPalette()
    SVGLoader.colorPatcherProvider = palette
    digests.add(theme.digest())

    // a palette that changes its digest without a new mod count
    val modCount = pathTransformGlobalModCount.get()
    palette.digest = longArrayOf(7)
    digests.add(theme.digest())
    assertEquals(modCount, pathTransformGlobalModCount.get(), "guard: the palette change does not change the mod count")

    IconLoader.setFilter(RotateChannelsFilter())
    digests.add(theme.digest())

    IconLoader.setUseDarkIcons(true)
    digests.add(theme.digest())

    assertEquals(digests.size, digests.toSet().size, "each change gives a new digest: $digests")
    assertTrue(digests.last().contains("dark=true"), digests.last())
  }

  @Test
  fun `a SwingIcon follows a palette change, a filter change and a dark switch like the legacy icon`() = runInEdtAndWait {
    val icon = native(SWITCH_ICON)
    val bridged = bridged(legacy(SWITCH_ICON))
    fun assertMatchesLegacy(label: String): Painted {
      val expected = paint(legacy(SWITCH_ICON))
      assertPixelsEqual(expected, paint(icon), "$label, native")
      assertPixelsEqual(expected, paint(bridged), "$label, bridged")
      return expected
    }

    assertEquals(GRAY, assertMatchesLegacy("plain").right)

    SVGLoader.colorPatcherProvider = PALETTE
    assertEquals(RED, assertMatchesLegacy("palette").right)

    IconLoader.setFilter(RotateChannelsFilter())
    assertEquals(GREEN, assertMatchesLegacy("palette and filter").right)

    IconLoader.setUseDarkIcons(true)
    val dark = assertMatchesLegacy("dark, palette and filter")
    assertEquals(GREEN, dark.left)
    assertEquals(BLUE, dark.right)
  }

  @Test
  fun `each theme change rebuilds the renderers once`() = runInEdtAndWait {
    val provider = CountingImageResourceProvider(IntelliJImageResourceProvider())
    val renderer = createRenderer(imageIconDescriptor(SWITCH_ICON, classLoader), ideTheme(), provider)
    fun paintThrice(label: String, loads: Int) {
      val expected = paint(legacy(SWITCH_ICON))
      repeat(3) { assertPixelsEqual(expected, paint(renderer), "$label, paint $it") }
      assertEquals(loads, provider.loads, "$label: the image loads")
    }

    paintThrice("plain", loads = 1)
    SVGLoader.colorPatcherProvider = PALETTE
    paintThrice("palette", loads = 2)
    IconLoader.setFilter(RotateChannelsFilter())
    paintThrice("filter", loads = 3)
    IconLoader.setUseDarkIcons(true)
    paintThrice("dark", loads = 4)
  }

  @Test
  fun `the update flow of an icon emits after a LaF change`() = runBlocking {
    val flow = IconRendererManager.createUpdateFlow(this)
    val received = CompletableDeferred<Int>()
    val collector = launch { flow.collect { received.complete(it) } }
    try {
      withTimeout(10.seconds) {
        // the collector subscribes asynchronously, so publish until it receives an update
        while (!received.isCompleted) {
          ApplicationManager.getApplication().messageBus.syncPublisher(LafManagerListener.TOPIC).lookAndFeelChanged(LafManager.getInstance())
          delay(10)
        }
      }
      assertEquals(-1, received.await())
    }
    finally {
      collector.cancel()
    }
  }

  // ---- a custom theme ----

  @Test
  fun `a custom theme supplies its own palette and filter`() = runInEdtAndWait {
    val filter = RotateChannelsFilter()
    SVGLoader.colorPatcherProvider = PALETTE
    IconLoader.setFilter(filter)
    val expected = paint(legacy(SWITCH_ICON))
    assertEquals(GREEN, expected.right, "guard: the legacy icon takes the palette and the filter")
    pathTransform.set(previousTransform)
    IconLoader.setUseDarkIcons(false)
    SVGLoader.colorPatcherProvider = OTHER_PALETTE
    IconLoader.clearCacheInTests()

    val theme = FixedTheme(palette = IntelliJThemeSvgPatcher(PALETTE), filter = IntelliJThemeImageFilter(filter), dark = false)
    val renderer = createRenderer(imageIconDescriptor(SWITCH_ICON, classLoader), theme, IntelliJImageResourceProvider())
    assertPixelsEqual(expected, paint(renderer), "the palette and the filter of the custom theme, not the global ones")
  }

  @Test
  fun `a custom theme supplies its own dark state`() = runInEdtAndWait {
    val expected = paint(IconLoader.getDarkIcon(legacy(SWITCH_ICON), true))
    assertEquals(GREEN, expected.right, "guard: the legacy dark artwork")

    val theme = FixedTheme(palette = null, filter = null, dark = true)
    val renderer = createRenderer(imageIconDescriptor(SWITCH_ICON, classLoader), theme, IntelliJImageResourceProvider())
    assertPixelsEqual(expected, paint(renderer), "the dark artwork of the custom theme")
  }

  @Test
  fun `a theme without inputs loads the plain image`() = runInEdtAndWait {
    val plain = paint(legacy(SWITCH_ICON))
    SVGLoader.colorPatcherProvider = PALETTE
    IconLoader.setFilter(RotateChannelsFilter())

    val renderer = createRenderer(imageIconDescriptor(SWITCH_ICON, classLoader), ThemeContext.None, IntelliJImageResourceProvider())
    assertPixelsEqual(plain, paint(renderer), "no theme inputs")

    val unknown = FixedTheme(palette = UnknownPatcher, filter = UnknownFilter, dark = null)
    val unknownRenderer = createRenderer(imageIconDescriptor(SWITCH_ICON, classLoader), unknown, IntelliJImageResourceProvider())
    assertPixelsEqual(plain, paint(unknownRenderer), "theme inputs of another backend")
  }

  @Test
  fun `a theme that only overrides imageModifiers keeps its dark state`() {
    val theme = object : ThemeContext {
      override fun digest(): String = "legacy"

      @Deprecated("Use isDark(), colorPalette() and globalFilter()")
      override fun imageModifiers(): ImageModifiers = DefaultImageModifiers(isDark = true)
    }
    assertEquals(true, theme.darkState())
    val context = DefaultRenderingContext(IconRendererManager.createUpdateFlow(null), null, theme, IntelliJImageResourceProvider())
    val layer = (imageIconDescriptor(SWITCH_ICON, classLoader) as DefaultLayeredIconDescriptor).layers.single()
    assertEquals(true, layer.generateImageModifiers(context).isDark)
  }

  private fun ideTheme(): ThemeContext = IconRendererManager.createRenderingContext(IconRendererManager.createUpdateFlow(null)).theme

  private fun createRenderer(descriptor: IconDescriptor, theme: ThemeContext, provider: ImageResourceProvider): IconRenderer {
    val context = DefaultRenderingContext(IconRendererManager.createUpdateFlow(null), null, theme, provider)
    return IconRendererManager.getInstance().createRenderer(descriptor, context)
  }

  private fun native(path: String): SwingIcon = imageIconDescriptor(path, classLoader).createSwingIcon() as SwingIcon

  private fun legacy(path: String): Icon = IconLoader.findIcon(path, classLoader)!!

  private fun bridged(legacy: Icon): SwingIcon = SwingIcon(DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, IconModifier))))

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

  /** [renderer] painted in device pixels at [density], as a [SwingIcon] paints its renderer. */
  private fun paint(renderer: IconRenderer, density: Float = 2f): Painted {
    val scaling = renderer.resolve(density, null)
    val width = scaling.finalDimensions.width
    val height = scaling.finalDimensions.height
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      renderer.render(SwingLayerPaintingContext(null, g, 0, 0, scaling = scaling.context))
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
                                 "(right #${Integer.toHexString(actual.right)}, expected #${Integer.toHexString(expected.right)})")
  }

  private class FixedTheme(
    private val palette: ThemeSvgPatcher?,
    private val filter: ThemeImageFilter?,
    private val dark: Boolean?,
  ) : ThemeContext {
    override fun digest(): String = "fixed"

    override fun isDark(): Boolean? = dark

    override fun colorPalette(): ThemeSvgPatcher? = palette

    override fun globalFilter(): ThemeImageFilter? = filter
  }

  private object UnknownPatcher : ThemeSvgPatcher {
    override fun digest(): String = "unknown"
  }

  private object UnknownFilter : ThemeImageFilter {
    override fun digest(): String = "unknown"
  }

  private class CountingImageResourceProvider(private val delegate: ImageResourceProvider) : ImageResourceProvider {
    var loads = 0

    override fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?): ImageResource =
      throw AssertionError("the rendering context gives its theme to the provider")

    override fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?, theme: ThemeContext): ImageResource {
      loads++
      return delegate.loadImage(location, imageModifiers, theme)
    }
  }

  private class MutableDigestPalette : SVGLoader.SvgElementColorPatcherProvider {
    @Volatile
    var digest: LongArray = longArrayOf(1)

    override fun digest(): LongArray = digest
  }

  /** Moves red to green, green to blue and blue to red. */
  private class RotateChannelsFilter : RGBImageFilter() {
    override fun filterRGB(x: Int, y: Int, rgb: Int): Int {
      val r = rgb shr 16 and 0xff
      val g = rgb shr 8 and 0xff
      val b = rgb and 0xff
      return (rgb and -0x1000000) or (b shl 16) or (r shl 8) or g
    }
  }

  private companion object {
    const val SWITCH_ICON = "iconsTest/theme/switch.svg"
    const val GRAY = 0xFF6C707E.toInt()
    const val RED = 0xFFFF0000.toInt()
    const val GREEN = 0xFF00FF00.toInt()
    const val BLUE = 0xFF0000FF.toInt()

    val PALETTE = object : SVGLoader.SvgElementColorPatcherProvider {
      private val patcher = newSvgPatcher(mapOf("#6c707e" to "#ff0000"), alphaProvider = { null })
      override fun attributeForPath(path: String) = patcher
      override fun digest(): LongArray = longArrayOf(42, 4242)
    }

    val OTHER_PALETTE = object : SVGLoader.SvgElementColorPatcherProvider {
      private val patcher = newSvgPatcher(mapOf("#6c707e" to "#ffff00"), alphaProvider = { null })
      override fun attributeForPath(path: String) = patcher
      override fun digest(): LongArray = longArrayOf(43, 4343)
    }
  }
}
