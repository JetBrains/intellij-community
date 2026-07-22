// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.impl.design.DefaultSRGB
import com.intellij.platform.icons.impl.patchers.authoredStrokeSvgPatcher
import com.intellij.platform.icons.impl.patchers.DefaultSvgPatcher
import com.intellij.platform.icons.impl.patchers.SvgPatchOperation
import com.intellij.platform.icons.impl.patchers.strokeSvgPatcher
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

/**
 * Pins the shape of the stroke patch itself: which colors it covers, which attributes it touches, and how selectively.
 *
 * New UI icons are authored in two styles — `<path fill="#6C707E">` and `<path fill="none" stroke="#6C707E">` — so a
 * substitution that touches only one attribute silently leaves every icon of the other style in its authored gray. It
 * also has to stay a palette substitution rather than a blanket one, since replacing every `fill` outright erases
 * icons drawn from filled shapes. Palette colors can be spelled as hex or as a keyword, and both have to match.
 *
 * The expected colors are spelled out here rather than read back from the patcher's own palette, so that dropping one
 * fails the test instead of quietly shrinking what it checks. They mirror the legacy Swing stroke patcher in
 * `com.intellij.ui.icons`, which is the palette both are expected to agree on.
 */
@TestApplication
class StrokePatcherTest {
  // Built directly, not via sRGB(), which would need the IconManager before it is activated.
  private val red = DefaultSRGB.fromHex("#FF0000")

  @BeforeEach
  fun activateIconManager() {
    // strokeSvgPatcher builds its patcher through the IconManager, so this class cannot rely on some other test
    // having activated it first.
    IntelliJIconManager.activate()
  }

  @Test
  fun `stroking recolors every foreground palette color, on both attributes`() {
    for (color in FOREGROUND) {
      assertRecolored(color, "foreground palette color")
    }
  }

  @Test
  fun `stroking recolors colors spelled as keywords`() {
    // fill="black" and fill="#000000" are the same color to a renderer, but only one of them matches a hex palette.
    for (keyword in FOREGROUND_KEYWORDS) {
      assertRecolored(keyword, "color keyword")
    }
  }

  @Test
  fun `stroking drops every background palette color, on both attributes`() {
    for (color in BACKGROUND) {
      for (attribute in ATTRIBUTES) {
        val match = conditionalReplacement(attribute, color)
        assertNotNull(match) { "no $attribute replacement for background palette color $color" }
        assert(match.value == "transparent") { "$attribute for $color became ${match.value}, wanted transparent" }
      }
    }
  }

  @Test
  fun `stroking patches nothing beyond the palette`() {
    // Every operation has to be accounted for: a stray one is a substitution nobody asked for.
    val expected = (FOREGROUND.size + FOREGROUND_KEYWORDS.size + BACKGROUND.size) * ATTRIBUTES.size
    val actual = operations().size
    assert(actual == expected) { "expected $expected operations for the palette, got $actual: ${operations()}" }
  }

  @Test
  fun `stroking never blanks an attribute unconditionally`() {
    // An unconditional replace is what erases icons: it drops every fill, palette color or not.
    val unconditional = operations().filter { !it.conditional }
    assert(unconditional.isEmpty()) { "unconditional operations would erase off-palette artwork: $unconditional" }
  }

  @Test
  fun `stroking a hand-authored variant recolors the white it is drawn in, on both attributes`() {
    for (value in AUTHORED_STROKE_COLORS) {
      for (attribute in ATTRIBUTES) {
        val match = conditionalReplacement(attribute, value, operations(authored = true))
        assertNotNull(match) { "no $attribute replacement for $value in a hand-authored stroke variant" }
        assert(match.value == red.toHex()) { "$attribute for $value became ${match.value}, wanted red" }
      }
    }
  }

  @Test
  fun `stroking a hand-authored variant reduces no palette color`() {
    // The variant is already an outline; dropping a background tint out of it could only take away artwork, since a
    // tint that reads as a background in a filled icon can be the outline itself in a drawing made of outlines.
    val expected = AUTHORED_STROKE_COLORS.size * ATTRIBUTES.size
    val actual = operations(authored = true).size
    assert(actual == expected) {
      "expected $expected operations to recolor a hand-authored variant, got $actual: ${operations(authored = true)}"
    }
  }

  @Test
  fun `stroking a hand-authored variant in opaque white patches nothing`() {
    // The variant is already drawn in white, so it is left exactly as authored — including any opacity it carries,
    // which recoloring white to white would still normalize away.
    val operations = operations(authored = true, stroke = DefaultSRGB.fromHex("#FFFFFFFF"))
    assert(operations.isEmpty()) { "stroking white must leave a hand-authored variant untouched, got $operations" }
  }

  @Test
  fun `stroking a hand-authored variant in translucent white still recolors`() {
    // Translucent white is a different color from the white the variant is drawn in, so it is a real substitution.
    val operations = operations(authored = true, stroke = DefaultSRGB.fromHex("#FFFFFF80"))
    assert(operations.isNotEmpty()) { "translucent white is not the color the variant is authored in" }
  }

  private fun assertRecolored(value: String, what: String) {
    for (attribute in ATTRIBUTES) {
      val match = conditionalReplacement(attribute, value)
      assertNotNull(match) { "no $attribute replacement for $what $value" }
      assert(match.value == red.toHex()) { "$attribute for $value became ${match.value}, wanted red" }
    }
  }

  private fun conditionalReplacement(
    attribute: String,
    expectedValue: String,
    operations: List<SvgPatchOperation> = operations(),
  ): SvgPatchOperation? =
    operations.singleOrNull {
      it.attributeName == attribute &&
      it.operation == SvgPatchOperation.Operation.Replace &&
      it.conditional &&
      it.expectedValue == expectedValue
    }

  private fun operations(authored: Boolean = false, stroke: Color = red): List<SvgPatchOperation> {
    val patcher = (if (authored) authoredStrokeSvgPatcher(stroke) else strokeSvgPatcher(stroke)) as? DefaultSvgPatcher
    assertNotNull(patcher)
    return patcher.operations
  }

  private companion object {
    private val ATTRIBUTES = listOf("fill", "stroke")

    // Lowercase, because that is what Color.toHex() produces and therefore what the conditions are built from.
    private val FOREGROUND =
      listOf(
        "#000000", "#ffffff", "#818594", "#6c707e", "#3574f0", "#5fb865", "#e35252",
        "#eb7171", "#e3ae4d", "#fcc75b", "#f28c35", "#955ae0", "#a8adbd", "#ced0d6",
      )

    private val FOREGROUND_KEYWORDS = listOf("black", "white")

    private val BACKGROUND =
      listOf(
        "#ebecf0", "#e7effd", "#dff2e0", "#f2fcf3", "#ffe8e8", "#fff5f5", "#fff8e3", "#fff4eb", "#eee0ff",
      )

    // A hand-authored stroke variant is drawn in white alone, in either spelling an SVG may use for it.
    private val AUTHORED_STROKE_COLORS = listOf("white", "#ffffff")
  }
}
