// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.deferredIconDescriptor
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.impl.modifiers.SizeVariantModifier
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.modifiers.sizeVariants
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import javax.swing.Icon

/**
 * [SizeVariantPolicy] via `IconModifier.sizeVariants`, and how it propagates to nested image layers.
 *
 * Artwork (iconsTest/variant): base `test.svg` 16×16 (red), `test@20x20.svg` (blue), `test@14x14.svg` (cyan).
 */
@TestApplication
class SizeVariantPolicyTest {
  private var previousIconManager: IconManager? = null
  private var previousRendererManager: IconRendererManager? = null
  private val classLoader: ClassLoader = SizeVariantPolicyTest::class.java.classLoader

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

  // ---- policies on a plain image ----

  @Test
  fun `Exact at 20 uses @20x20`() = runInEdtAndWait { assertEquals(BLUE, center(image(SizeVariantPolicy.Exact), 1.25f)) }

  @Test
  fun `Exact at 24 keeps the scaled base`() = runInEdtAndWait { assertEquals(RED, center(image(SizeVariantPolicy.Exact), 1.5f)) }

  @Test
  fun `Fitting at 24 with only @20x20 below uses @20x20 scaled up`() = runInEdtAndWait {
    assertEquals(BLUE, center(image(SizeVariantPolicy.Fitting), 1.5f))
  }

  @Test
  fun `Fitting at 20 uses @20x20`() = runInEdtAndWait { assertEquals(BLUE, center(image(SizeVariantPolicy.Fitting), 1.25f)) }

  @Test
  fun `Fitting at 18 with @14x14 below the base size keeps the scaled base`() = runInEdtAndWait {
    assertEquals(RED, center(image(SizeVariantPolicy.Fitting), 1.125f))
  }

  @Test
  fun `None at 20 keeps the scaled base`() = runInEdtAndWait { assertEquals(RED, center(image(SizeVariantPolicy.None), 1.25f)) }

  @Test
  fun `no modifier means Exact`() = runInEdtAndWait {
    assertEquals(BLUE, center(imageIconDescriptor(TEST, classLoader), 1.25f))
    assertEquals(RED, center(imageIconDescriptor(TEST, classLoader), 1.5f))
  }

  // ---- propagation ----

  @Test
  fun `policy on an outer nested layer applies to the inner image layer`() = runInEdtAndWait {
    val inner = imageIconDescriptor(TEST, classLoader)
    assertEquals(RED, center(iconDescriptor { icon(inner, IconModifier.sizeVariants(SizeVariantPolicy.None)) }, 1.25f))
    assertEquals(BLUE, center(iconDescriptor { icon(inner, IconModifier.sizeVariants(SizeVariantPolicy.Fitting)) }, 1.5f))
  }

  @Test
  fun `inner override beats the outer policy`() = runInEdtAndWait {
    val inner = image(SizeVariantPolicy.Exact)
    assertEquals(BLUE, center(iconDescriptor { icon(inner, IconModifier.sizeVariants(SizeVariantPolicy.None)) }, 1.25f))
  }

  @Test
  fun `policy on a layout layer applies to its children`() = runInEdtAndWait {
    val descriptor = iconDescriptor { row(IconModifier.sizeVariants(SizeVariantPolicy.None)) { image(TEST, classLoader) } }
    assertEquals(RED, center(descriptor, 1.25f))
    val fitting = iconDescriptor { box(IconModifier.sizeVariants(SizeVariantPolicy.Fitting)) { image(TEST, classLoader) } }
    assertEquals(BLUE, center(fitting, 1.5f))
  }

  @Test
  fun `policy on an animation layer applies to its frames`() = runInEdtAndWait {
    val descriptor = iconDescriptor {
      animation(IconModifier.sizeVariants(SizeVariantPolicy.None)) {
        frame(1_000_000) { image(TEST, classLoader) }
      }
    }
    assertEquals(RED, center(descriptor, 1.25f))
  }

  /**
   * The deferred renderer renders its placeholder (and later the resolved icon) with its own rendering context, so their
   * policies come through unchanged. (A deferred descriptor cannot be wrapped in a NestedIconLayer at all today:
   * NestedIconLayerRenderer only supports DefaultIconRenderer — unrelated to size variants.)
   */
  @Test
  fun `deferred icon placeholder keeps its policy`() = runInEdtAndWait {
    val none = deferredIconDescriptor(image(SizeVariantPolicy.None)) { image(SizeVariantPolicy.None) }
    assertEquals(RED, center(none, 1.25f))
    val plain = deferredIconDescriptor(imageIconDescriptor(TEST, classLoader)) { imageIconDescriptor(TEST, classLoader) }
    assertEquals(BLUE, center(plain, 1.25f))
  }

  @Test
  fun `policy reaches bridged swing layers, own or inherited`() = runInEdtAndWait {
    val legacy = IconLoader.findIcon(TEST, classLoader)!!
    val own = DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, IconModifier.sizeVariants(SizeVariantPolicy.None))))
    assertEquals(RED, center(own, 1.25f))
    val plain = DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, IconModifier)))
    assertEquals(BLUE, center(plain, 1.25f))
    val inherited = DefaultLayeredIconDescriptor(listOf(NestedIconLayer(plain, IconModifier.sizeVariants(SizeVariantPolicy.None))))
    assertEquals(RED, center(inherited, 1.25f))
    val fitting = DefaultLayeredIconDescriptor(listOf(NestedIconLayer(plain, IconModifier.sizeVariants(SizeVariantPolicy.Fitting))))
    assertEquals(BLUE, center(fitting, 1.5f))
  }

  @Test
  fun `modifier serialization round trip`() {
    for (policy in SizeVariantPolicy.entries) {
      val modifier = SizeVariantModifier(policy)
      val json = Json.encodeToString(SizeVariantModifier.serializer(), modifier)
      assertEquals(modifier, Json.decodeFromString(SizeVariantModifier.serializer(), json), json)
    }
  }

  private fun image(policy: SizeVariantPolicy): IconDescriptor =
    imageIconDescriptor(TEST, classLoader, IconModifier.sizeVariants(policy))

  /** ARGB at the center after painting [descriptor] scaled by [factor] at density 2. */
  private fun center(descriptor: IconDescriptor, factor: Float): Int {
    val icon = descriptor.createSwingIcon().let { if (factor == 1f) it else it.scaled(factor(factor)) } as Icon
    val width = icon.iconWidth * 2
    val height = icon.iconHeight * 2
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      g.scale(2.0, 2.0)
      icon.paintIcon(null, g, 0, 0)
    }
    finally {
      g.dispose()
    }
    return image.getRGB(width / 2, height / 2)
  }

  private companion object {
    const val TEST = "iconsTest/variant/test.svg"
    const val RED = 0xFFFF0000.toInt()
    const val BLUE = 0xFF0000FF.toInt()
  }
}
