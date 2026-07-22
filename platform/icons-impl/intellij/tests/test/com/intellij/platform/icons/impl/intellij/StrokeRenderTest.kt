// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.imageIcon
import com.intellij.platform.icons.impl.design.DefaultSRGB
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.patchSvg
import com.intellij.platform.icons.modifiers.stroke
import com.intellij.platform.icons.patchers.svgPatcher
import com.intellij.platform.icons.swing.toSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import javax.swing.UIManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Renders real New UI icons through the actual Swing path — `imageIcon(...).toSwingIcon().paintIcon(...)`, the same one
 * a Swing toolbar takes — and inspects the resulting pixels.
 *
 * Stroking only holds end to end if every stage agrees: the patcher has to be built, survive being combined with
 * whatever else patches the icon, reach the renderer, and match the color values the SVG was actually authored with.
 * Asserting over the patch description alone sees none of that, so this asserts on the rasterized output.
 *
 * Both authoring styles New UI icons come in are covered, since a substitution that handles one can silently miss the
 * other: fill-authored (`<path fill="#6C707E">`, e.g. softWrap) and stroke-authored
 * (`<path fill="none" stroke="#6C707E">`, e.g. save and inlaySettings). Each is checked in both states — unstroked it
 * keeps its authored gray, stroked it is fully recolored with none of that gray left behind.
 */
@TestApplication
class StrokeRenderTest {
  // Built directly, not via sRGB(), which would need the IconManager before it is activated.
  private val red = DefaultSRGB.fromHex("#FF0000")

  @Test
  fun `fill-authored icon strokes red`() {
    assertStrokedRed("expui/general/softWrap.svg")
  }

  @Test
  fun `stroke-authored icon strokes red`() {
    assertStrokedRed("expui/general/save.svg")
  }

  @Test
  fun `another stroke-authored icon strokes red`() {
    assertStrokedRed("expui/codeInsight/inlaySettings.svg")
  }

  @Test
  fun `icons stroke red in a dark theme too`() {
    // A dark variant is authored in its own grays (inlaySettings_dark.svg is #9DA0A8), which are not stroke palette
    // colors. Resolving it while stroking would leave the icon in those grays, so stroking must take the light artwork.
    val path = "expui/codeInsight/inlaySettings.svg"
    val light = render(path, IconModifier)

    withDarkTheme {
      // Guard: without this the theme override could silently not reach the loader and the test would prove nothing.
      val dark = render(path, IconModifier)
      assertTrue(!sameImage(light, dark)) { "$path rendered identically in both themes, so the dark theme never applied" }

      // Assert on "every opaque pixel is red" rather than on the absence of a specific gray: which gray an unstroked
      // icon shows is theme-dependent, but a fully recolored glyph is not.
      assertFullyRed(path)
      assertFullyRed("expui/general/softWrap.svg")
    }
  }

  private fun assertFullyRed(path: String) {
    val stroked = count(render(path, IconModifier.stroke(red)))
    println("[stroke-render/dark] $path  stroked=[$stroked]")
    assertTrue(stroked.opaque > 0) { "$path: the stroked icon rendered nothing at all" }
    assertEquals(stroked.opaque, stroked.red, "$path: stroking left non-red pixels behind, got $stroked")
  }

  @Test
  fun `an icon's own patcher wins over the stroke substitution`() {
    // Both target #6C707E. The icon's patcher runs first, so the stroke operation finds green and no longer matches;
    // reversing the order would silently override whatever an icon deliberately recolors.
    val path = "expui/general/softWrap.svg"
    val green = DefaultSRGB.fromHex("#00FF00")
    val ownPatcher = svgPatcher { replaceIfMatches("fill", "#6c707e", green.toHex()) }
    val counts = count(render(path, IconModifier.patchSvg(ownPatcher).stroke(red)))

    println("[stroke-render/order] $path  $counts")
    // Both halves matter: green proves the icon's own patcher ran at all, and no red proves stroking did not
    // override it. Asserting only the absence of red would also pass on a blank or unpatched render.
    assertTrue(counts.green > 0) { "$path: the icon's own patcher did not take effect, got $counts" }
    assertEquals(0, counts.red, "$path: the stroke color overrode the icon's own patcher, got $counts")
  }

  @Test
  fun `icon with a hand-authored stroke variant strokes red`() {
    // run.svg is a palette background fill inside an off-palette green outline, so reducing it to an outline cannot
    // produce the stroked glyph — the icon ships run_stroke.svg, a separate drawing, for exactly that.
    val stroked = count(render("expui/run/run.svg", IconModifier.stroke(red)))
    println("[stroke-render] expui/run/run.svg  stroked=[$stroked]")

    assertTrue(stroked.red > 0) { "expected stroking to produce red pixels, got $stroked" }
    assertEquals(0, stroked.green, "the authored green outline must not survive stroking, got $stroked")
  }

  @Test
  fun `stroking replaces the opacity a shape was authored with`() {
    // cursorText.svg draws one of its shapes at fill-opacity="0.7". That opacity described the color stroking has just
    // replaced, so it cannot go on shading the color that replaced it.
    val stroked = render("expui/windows/mouse/cursorText.svg", IconModifier.stroke(red))
    val authored = (0.7f * 255).roundToInt()
    val atAuthoredOpacity = (authored - 1..authored + 1).sumOf { alpha -> countAtAlpha(stroked, alpha) }
    println("[stroke-render] cursorText.svg  stroked=[${count(stroked)}]  atAuthoredOpacity=$atAuthoredOpacity")

    assertEquals(0, atAuthoredOpacity, "pixels are still rendered at the authored 0.7 opacity")
  }

  private fun countAtAlpha(image: BufferedImage, alpha: Int): Int {
    var count = 0
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        if (image.getRGB(x, y) ushr 24 and 0xFF == alpha) count++
      }
    }
    return count
  }

  private fun assertStrokedRed(path: String) {
    val plain = count(render(path, IconModifier))
    val stroked = count(render(path, IconModifier.stroke(red)))
    println("[stroke-render] $path  plain=[$plain]  stroked=[$stroked]")

    // Control: the unstroked icon renders in its authored gray, never red.
    assertTrue(plain.gray > 0) { "$path: expected the unstroked icon to render gray pixels, got $plain" }
    assertEquals(0, plain.red, "$path: the unstroked icon must not contain red pixels, got $plain")

    // Stroking recolors the whole glyph: red appears, and none of the authored gray survives.
    assertTrue(stroked.red > 0) { "$path: expected stroking to produce red pixels, got $stroked" }
    assertEquals(0, stroked.gray, "$path: stroking must replace the authored gray, but gray pixels remain: $stroked")
  }

  private fun render(path: String, modifier: IconModifier): BufferedImage {
    IntelliJIconManager.activate()
    val swingIcon = imageIcon(path, StrokeRenderTest::class.java.classLoader, modifier).toSwingIcon()
    val image = BufferedImage(swingIcon.iconWidth, swingIcon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      swingIcon.paintIcon(null, g, 0, 0)
    } finally {
      g.dispose()
    }
    return image
  }

  private fun count(image: BufferedImage): Counts {
    var red = 0
    var green = 0
    var gray = 0
    var opaque = 0
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        val argb = image.getRGB(x, y)
        val a = argb ushr 24 and 0xFF
        if (a < 128) continue
        opaque++
        val r = argb ushr 16 and 0xFF
        val g = argb ushr 8 and 0xFF
        val b = argb and 0xFF
        // Strongly red: the stroke color took over the glyph.
        if (r > 140 && g < 110 && b < 110) red++
        // Strongly green: the icon's own patcher took over the glyph.
        if (g > 140 && r < 110 && b < 110) green++
        // The authored New UI gray (#6C707E ~ 108,112,126): near-equal channels in the mid range.
        if (r in 70..190 && kotlin.math.abs(r - g) < 24 && kotlin.math.abs(g - b) < 28) gray++
      }
    }
    return Counts(red, green, gray, opaque)
  }

  private fun withDarkTheme(action: () -> Unit) {
    val defaults = UIManager.getLookAndFeelDefaults()
    val previous = defaults.get(DARK_THEME_KEY)
    defaults.put(DARK_THEME_KEY, true)
    try {
      action()
    } finally {
      defaults.put(DARK_THEME_KEY, previous)
    }
  }

  private fun sameImage(a: BufferedImage, b: BufferedImage): Boolean {
    if (a.width != b.width || a.height != b.height) return false
    for (y in 0 until a.height) {
      for (x in 0 until a.width) {
        if (a.getRGB(x, y) != b.getRGB(x, y)) return false
      }
    }
    return true
  }

  private class Counts(val red: Int, val green: Int, val gray: Int, val opaque: Int) {
    override fun toString(): String = "red=$red green=$green gray=$gray opaque=$opaque"
  }

  private companion object {
    private const val DARK_THEME_KEY = "ui.theme.is.dark"
  }
}
