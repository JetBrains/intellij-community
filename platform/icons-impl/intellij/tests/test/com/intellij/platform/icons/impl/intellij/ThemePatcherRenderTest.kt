// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.util.SVGLoader
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import org.junit.jupiter.api.Assertions.assertTrue

@TestApplication
class ThemePatcherRenderTest {
  private val classLoader = ThemePatcherRenderTest::class.java.classLoader

  companion object {
    private const val PATH = "expui/general/add.svg"

    @JvmStatic
    @BeforeAll
    fun activateLegacyLoader() {
      IconLoader.activate()
    }

    @JvmStatic
    @AfterAll
    fun deactivateLegacyLoader() {
      IconLoader.deactivate()
    }
  }

  @BeforeEach
  fun activateIconManager() {
    IntelliJIconManager.activate()
  }

  @Test
  fun `theme color patcher recolors icons like the legacy loader`() {
    val previous = SVGLoader.colorPatcherProvider
    SVGLoader.colorPatcherProvider = GrayToGreenPalette
    try {
      val legacy = IconLoader.getIcon(PATH, classLoader).paintToImage()
      val modern = imageIconDescriptor(PATH, classLoader).createSwingIcon().paintToImage()
      println("[theme-patcher] legacy green=${greenPixels(legacy)} modern green=${greenPixels(modern)}")

      assertTrue(greenPixels(legacy) > 0, "the control failed: the legacy icon ignored the theme patcher, so the test proves nothing")
      assertTrue(greenPixels(modern) > 0, "the new pipeline ignored the theme color patcher")
    }
    finally {
      SVGLoader.colorPatcherProvider = previous
    }
  }

  private fun greenPixels(image: BufferedImage): Int {
    var count = 0
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        val argb = image.getRGB(x, y)
        if (argb ushr 24 and 0xFF < 128) continue
        if ((argb ushr 8 and 0xFF) > 140 && (argb ushr 16 and 0xFF) < 110 && (argb and 0xFF) < 110) count++
      }
    }
    return count
  }

  private object GrayToGreenPalette : SVGLoader.SvgElementColorPatcherProvider {
    override fun attributeForPath(path: String): SvgAttributePatcher = object : SvgAttributePatcher {
      override fun patchColors(attributes: MutableMap<String, String>) {
        if (attributes["fill"].equals("#6C707E", ignoreCase = true)) attributes["fill"] = "#00FF00"
      }
    }
    override fun digest(): LongArray = longArrayOf(42L, 7L)
  }
}
