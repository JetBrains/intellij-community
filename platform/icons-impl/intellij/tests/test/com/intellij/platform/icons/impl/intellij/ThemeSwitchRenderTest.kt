// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingLayerPaintingContext
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.impl.rendering.DefaultScalingContext
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceProvider
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.Icon

@TestApplication
class ThemeSwitchRenderTest {
  private val classLoader = ThemeSwitchRenderTest::class.java.classLoader

  @BeforeEach
  fun activateIconManager() {
    IntelliJIconManager.activate()
  }

  @Test
  fun `reused image icon paints the new theme`() {
    withDarkIcons(dark = false) {
      val descriptor = imageIconDescriptor(PATH, classLoader)
      val reused = descriptor.createSwingIcon()
      val before = reused.paintToImage()

      withDarkIcons {
        val expected = descriptor.createSwingIcon().paintToImage()
        val actual = reused.paintToImage()
        assertFalse(sameImage(before, actual), "the reused icon did not change after the switch")
        assertTrue(sameImage(expected, actual), "the reused icon paints other pixels than a new icon after the switch")
      }
    }
  }

  @Test
  fun `caller modifiers keep the live dark flag`() {
    withDarkIcons(dark = false) {
      val descriptor = imageIconDescriptor(PATH, classLoader)
      val light = paint(descriptor, callerModifiers = null)

      withDarkIcons {
        val dark = paint(descriptor, callerModifiers = null)
        val withCaller = paint(descriptor, callerModifiers = DefaultImageModifiers())
        assertFalse(sameImage(light, dark), "$PATH paints the same in both themes, so the test proves nothing")
        assertTrue(sameImage(dark, withCaller), "caller modifiers without a dark flag painted the light artwork")
      }
    }
  }

  @Test
  fun `renderer builds its layers once per theme switch`() {
    val theme = SwitchableTheme()
    val images = CountingImages()
    val renderer = rendererFor(theme, images)
    assertEquals(1, images.loads, "the renderer builds its layers when it is created")

    renderer.paint()
    renderer.paint()
    assertEquals(1, images.loads, "the renderer built its layers again without a theme change")

    theme.digest = "dark"
    renderer.paint()
    renderer.paint()
    assertEquals(2, images.loads, "the renderer did not build its layers exactly once after the switch")
  }

  @Test
  fun `renderer tries again after a build fails`() {
    val theme = SwitchableTheme()
    val images = CountingImages()
    val renderer = rendererFor(theme, images)

    theme.digest = "dark"
    images.failNextLoad = true
    assertThrows<IllegalStateException> { renderer.paint() }
    renderer.paint()
    assertEquals(3, images.loads, "the renderer kept the old layers after a failed build")
  }

  @Test
  fun `a switch during the first build is not lost`() {
    val theme = SwitchableTheme()
    val images = CountingImages().apply { onLoad = { if (loads == 1) theme.digest = "dark" } }
    val renderer = rendererFor(theme, images)

    renderer.paint()
    assertEquals(2, images.loads, "the renderer kept layers built for the theme from before the switch")
  }

  @Test
  fun `a switch changes the used size before the next paint`() {
    val theme = SwitchableTheme()
    val images = CountingImages()
    val renderer = rendererFor(theme, images)

    images.size = 24
    theme.digest = "dark"
    val used = renderer.calculateUsedDimensions(DefaultScalingContext(1f, 1f))
    assertEquals(24, used.width, "the used size still comes from the layers of the old theme")
  }

  @Test
  fun `a switch changes the size that the icon reports to Swing`() {
    // open.svg is 20x20 and open_dark.svg is 21x20. Swing lays out a component with iconWidth, so a stale value clips the icon.
    withDarkIcons(dark = false) {
      val icon = imageIconDescriptor(SIZE_CHANGING_PATH, classLoader).createSwingIcon()
      assertEquals(20, icon.iconWidth, "the light artwork of $SIZE_CHANGING_PATH is 20 wide")

      withDarkIcons {
        assertEquals(21, icon.iconWidth, "the icon reports the width of the light artwork after the switch")
      }
    }
  }

  private fun paint(descriptor: IconDescriptor, callerModifiers: DefaultImageModifiers?): BufferedImage {
    val flow = IconRendererManager.createUpdateFlow(null) {}
    val context = IconRendererManager.createRenderingContext(flow, callerModifiers)
    return rendererFor(descriptor, context).toSwingIcon().paintToImage()
  }

  private fun rendererFor(descriptor: IconDescriptor, context: RenderingContext): IconRenderer =
    IconRendererManager.getInstance().createRenderer(descriptor, context)

  private fun rendererFor(theme: ThemeContext, images: ImageResourceProvider): IconRenderer {
    val descriptor = iconDescriptor { image(FakeImage) }
    val flow = IconRendererManager.createUpdateFlow(null) {}
    return rendererFor(descriptor, DefaultRenderingContext(flow, null, theme, images))
  }

  private fun IconRenderer.paint() {
    IconTestRun(this).performRender()
  }

  private fun IconRenderer.toSwingIcon(): Icon = object : Icon {
    override fun getIconWidth() = 16
    override fun getIconHeight() = 16
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
      render(SwingLayerPaintingContext(c, g, x, y))
    }
  }

  private class SwitchableTheme : ThemeContext {
    var digest = "light"
    override fun digest(): String = digest
  }

  private class CountingImages : ImageResourceProvider {
    var loads = 0
    var size = 16
    var failNextLoad = false
    var onLoad: (() -> Unit)? = null

    override fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?): ImageResource {
      loads++
      onLoad?.invoke()
      if (failNextLoad) {
        failNextLoad = false
        throw IllegalStateException("the image failed to load")
      }
      return TestImageResource(TestImageResourceLocation(size, size))
    }
  }

  private companion object {
    private const val PATH = "expui/general/add.svg"
    private const val SIZE_CHANGING_PATH = "expui/welcome/open.svg"
    private val FakeImage = TestImageResourceLocation(16, 16)
  }
}
