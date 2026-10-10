// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.forceDarkVariant
import com.intellij.platform.icons.modifiers.forceLightVariant
import com.intellij.platform.icons.modifiers.patchSvg
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.platform.icons.swing.toIconDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.LayeredIcon
import java.awt.image.BufferedImage
import javax.swing.Icon
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Renders a New UI icon that ships its own dark artwork (`inlaySettings_dark.svg`) through the Swing path.
 * The modifiers `forceDarkVariant` and `forceLightVariant` must pick the artwork, whatever the theme says.
 * The same holds for a legacy icon that the bridge wraps with `toIconDescriptor()`.
 */
@TestApplication
class ForceDarkVariantRenderTest {
  @BeforeEach
  fun activateIconManager() {
    IntelliJIconManager.activate()
  }

  @Test
  fun `forcing the dark variant in a bright theme renders the dark artwork`() {
    val bright = render(IconModifier)
    val darkTheme = withDarkIcons { render(IconModifier) }

    assertDifferentImage(bright, darkTheme, "the icon renders the same in both themes, so the test proves nothing")

    assertSameImage(darkTheme, render(IconModifier.forceDarkVariant()), "the forced dark variant differs from the dark theme artwork")
  }

  @Test
  fun `forcing the light variant in a dark theme renders the light artwork`() {
    val forced = withDarkIcons { render(IconModifier.forceLightVariant()) }

    assertSameImage(render(IconModifier), forced, "the forced light variant differs from the bright theme artwork")
  }

  @Test
  fun `forcing the variant that the theme already uses changes nothing`() {
    assertSameImage(render(IconModifier), render(IconModifier.forceLightVariant()), "the forced light variant changed a bright theme")
    withDarkIcons {
      assertSameImage(render(IconModifier), render(IconModifier.forceDarkVariant()), "the forced dark variant changed a dark theme")
    }
  }

  @Test
  fun `modifiers with the same variant are equal`() {
    assertEquals(IconModifier.forceDarkVariant(), IconModifier.forceDarkVariant())
    assertEquals(IconModifier.forceDarkVariant().hashCode(), IconModifier.forceDarkVariant().hashCode())
    assertEquals(IconModifier.forceLightVariant(), IconModifier.forceLightVariant())
    assertNotEquals(IconModifier.forceDarkVariant(), IconModifier.forceLightVariant())
  }

  @Test
  fun `forcing the dark variant on a bridged legacy icon gives the dark artwork`() {
    val legacy = legacyIcon()
    assertHasTwoVariants(legacy)

    assertSameImage(
      legacy.variant(isDark = true),
      bridged(legacy, IconModifier.forceDarkVariant()),
      "the bridge ignores the forced dark variant",
    )
  }

  @Test
  fun `forcing the light variant on a bridged legacy icon gives the light artwork`() {
    val forcedDark = IconLoader.getDarkIcon(legacyIcon(), true)
    assertHasTwoVariants(forcedDark)

    assertSameImage(
      forcedDark.variant(isDark = false),
      bridged(forcedDark, IconModifier.forceLightVariant()),
      "the bridge ignores the forced light variant",
    )
  }

  @Test
  fun `forcing the dark variant on a wrapped legacy icon gives the dark artwork`() {
    val wrapped = LayeredIcon(legacyIcon())
    assertHasTwoVariants(wrapped)

    assertSameImage(
      wrapped.variant(isDark = true),
      bridged(wrapped, IconModifier.forceDarkVariant()),
      "the bridge ignores the forced dark variant",
    )
  }

  @Test
  fun `bridging a legacy icon without a modifier keeps the variant it already has`() {
    val forcedDark = IconLoader.getDarkIcon(legacyIcon(), true)
    assertHasTwoVariants(forcedDark)

    assertSameImage(forcedDark.paintToImage(), bridged(forcedDark, IconModifier), "the bridge changed the variant of the icon")
  }

  @Test
  fun `bridging a patched legacy icon without a forced variant keeps the variant it already has`() {
    val forcedDark = IconLoader.getDarkIcon(legacyIcon(), true)

    assertHasTwoVariants(forcedDark)

    val patch = IconModifier.patchSvg { replaceIfMatches("fill", "#123456", "#654321") }

    assertSameImage(forcedDark.paintToImage(), bridged(forcedDark, patch), "the bridge changed the variant of the patched icon")
  }

  private fun render(modifier: IconModifier): BufferedImage = render(PATH, modifier)

  private fun legacyIcon(): Icon = IconLoader.getIcon(PATH, AllIcons::class.java.classLoader)

  private fun Icon.variant(isDark: Boolean): BufferedImage = IconLoader.getDarkIcon(this, isDark).paintToImage()

  private fun assertHasTwoVariants(icon: Icon) {
    assertDifferentImage(icon.variant(isDark = true), icon.variant(isDark = false), "the icon has one artwork, so the test proves nothing")
  }

  private fun bridged(legacy: Icon, modifier: IconModifier): BufferedImage {
    val descriptor: IconDescriptor = iconDescriptor { icon(legacy.toIconDescriptor(), modifier) }
    return descriptor.createSwingIcon().paintToImage()
  }

  private companion object {
    private const val PATH = "expui/codeInsight/inlaySettings.svg"

    @JvmStatic
    @BeforeAll
    fun activateLegacyIconBridge() {
      IconLoader.activate()
    }

    @JvmStatic
    @AfterAll
    fun deactivateLegacyIconBridge() {
      IconLoader.deactivate()
    }
  }
}
