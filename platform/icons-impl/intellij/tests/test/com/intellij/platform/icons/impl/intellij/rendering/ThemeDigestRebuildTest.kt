// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.IconTestRun
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.impl.intellij.TestImageResource
import com.intellij.platform.icons.impl.intellij.TestImageResourceLocation
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceProvider
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A change of the theme digest rebuilds the layer renderers of an icon once, not on each later paint. */
@TestApplication
class ThemeDigestRebuildTest {
  private var previousIconManager: IconManager? = null
  private var previousRendererManager: IconRendererManager? = null

  @BeforeEach
  fun setUp() {
    previousIconManager = runCatching { IconManager.getInstance() }.getOrNull()
    previousRendererManager = runCatching { IconRendererManager.getInstance() }.getOrNull()
    IntelliJIconManager.activate()
  }

  @AfterEach
  fun tearDown() {
    previousIconManager?.let { IconManager.activate(it) }
    previousRendererManager?.let { IconRendererManager.activate(it) }
  }

  @Test
  fun `renderers are rebuilt once per digest change`() {
    val theme = MutableThemeContext()
    val provider = CountingImageResourceProvider()
    val context = DefaultRenderingContext(IconRendererManager.createUpdateFlow(null), null, theme, provider)
    val run = IconTestRun(IconRendererManager.getInstance().createRenderer(imageIconDescriptor("any.svg", ThemeDigestRebuildTest::class.java.classLoader), context))
    assertEquals(1, provider.loads, "the first build")

    repeat(3) { run.performRender() }
    assertEquals(1, provider.loads, "paints without a digest change")

    theme.digest = "second"
    repeat(3) { run.performRender() }
    assertEquals(2, provider.loads, "paints after one digest change")

    theme.digest = "third"
    run.performRender()
    run.performRender()
    assertEquals(3, provider.loads, "paints after another digest change")
  }

  private class MutableThemeContext : ThemeContext {
    @Volatile
    var digest: String = "first"

    override fun digest(): String = digest
  }

  private class CountingImageResourceProvider : ImageResourceProvider {
    var loads = 0

    override fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?): ImageResource {
      loads++
      return TestImageResource(TestImageResourceLocation(16, 16))
    }
  }
}
