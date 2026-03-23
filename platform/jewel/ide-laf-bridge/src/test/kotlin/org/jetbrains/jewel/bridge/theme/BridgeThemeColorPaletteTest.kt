// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import com.intellij.ui.IslandsState
import java.awt.Color
import javax.swing.UIManager
import kotlin.collections.set
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette
import org.junit.Assert.assertEquals
import org.junit.Test

internal class BridgeThemeColorPaletteTest {
    private val colorCount = 14

    @Test
    fun `should save non-islands gray colors to ThemeColorPalette`() {
        val colorName = "Gray"
        val expectedColors = createNonIslandThemeColors(colorName, colorCount)

        withUiDefaults(expectedColors) {
            val palette = ThemeColorPalette.readFromLaF()
            for (i in 1..colorCount) {
                val expected = expectedColors.getValue("ColorPalette.$colorName$i").toComposeColor()
                val actual = palette.grayOrNull(i)
                assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
            }
        }
    }

    @Test
    fun `should save islands gray colors to ThemeColorPalette`() {
        val colorName = "Gray"
        val expectedIslandColors = createIslandThemeColors(colorName, colorCount)

        inIslandsTheme {
            withUiDefaults(expectedIslandColors) {
                val palette = ThemeColorPalette.readFromLaF()
                for (i in 10..colorCount * 10 step 10) {
                    val expected =
                        expectedIslandColors.getValue("ColorPalette.${colorName.lowercase()}-$i").toComposeColor()
                    val actual = palette.grayOrNull(i)
                    assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
                }
            }
        }
    }

    @Test
    fun `should save blue colors to ThemeColorPalette`() {
        val colorName = "Blue"
        val expectedColors = createNonIslandThemeColors(colorName, colorCount)

        withUiDefaults(expectedColors) {
            val palette = ThemeColorPalette.readFromLaF()
            for (i in 1..colorCount) {
                val expected = expectedColors.getValue("ColorPalette.$colorName$i").toComposeColor()
                val actual = palette.blueOrNull(i)
                assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
            }
        }
    }

    @Test
    fun `should save islands blue colors to ThemeColorPalette`() {
        val colorName = "Blue"
        val expectedIslandColors = createIslandThemeColors(colorName, colorCount)

        inIslandsTheme {
            withUiDefaults(expectedIslandColors) {
                val palette = ThemeColorPalette.readFromLaF()
                for (i in 10..colorCount * 10 step 10) {
                    val expected =
                        expectedIslandColors.getValue("ColorPalette.${colorName.lowercase()}-$i").toComposeColor()
                    val actual = palette.blueOrNull(i)
                    assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
                }
            }
        }
    }

    @Test
    fun `should save green colors to ThemeColorPalette`() {
        val colorName = "Green"
        val expectedColors = createNonIslandThemeColors(colorName, colorCount)

        withUiDefaults(expectedColors) {
            val palette = ThemeColorPalette.readFromLaF()
            for (i in 1..colorCount) {
                val expected = expectedColors.getValue("ColorPalette.$colorName$i").toComposeColor()
                val actual = palette.greenOrNull(i)
                assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
            }
        }
    }

    @Test
    fun `should save islands green colors to ThemeColorPalette`() {
        val colorName = "Green"
        val expectedIslandColors = createIslandThemeColors(colorName, colorCount)

        inIslandsTheme {
            withUiDefaults(expectedIslandColors) {
                val palette = ThemeColorPalette.readFromLaF()
                for (i in 10..colorCount * 10 step 10) {
                    val expected =
                        expectedIslandColors.getValue("ColorPalette.${colorName.lowercase()}-$i").toComposeColor()
                    val actual = palette.greenOrNull(i)
                    assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
                }
            }
        }
    }

    @Test
    fun `should save red colors to ThemeColorPalette`() {
        val colorName = "Red"
        val expectedColors = createNonIslandThemeColors(colorName, colorCount)

        withUiDefaults(expectedColors) {
            val palette = ThemeColorPalette.readFromLaF()
            for (i in 1..colorCount) {
                val expected = expectedColors.getValue("ColorPalette.$colorName$i").toComposeColor()
                val actual = palette.redOrNull(i)
                assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
            }
        }
    }

    @Test
    fun `should save islands red colors to ThemeColorPalette`() {
        val colorName = "Red"
        val expectedIslandColors = createIslandThemeColors(colorName, colorCount)

        inIslandsTheme {
            withUiDefaults(expectedIslandColors) {
                val palette = ThemeColorPalette.readFromLaF()
                for (i in 10..colorCount * 10 step 10) {
                    val expected =
                        expectedIslandColors.getValue("ColorPalette.${colorName.lowercase()}-$i").toComposeColor()
                    val actual = palette.redOrNull(i)
                    assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
                }
            }
        }
    }

    @Test
    fun `should save yellow colors to ThemeColorPalette`() {
        val colorName = "Yellow"
        val expectedColors = createNonIslandThemeColors(colorName, colorCount)

        withUiDefaults(expectedColors) {
            val palette = ThemeColorPalette.readFromLaF()
            for (i in 1..colorCount) {
                val expected = expectedColors.getValue("ColorPalette.$colorName$i").toComposeColor()
                val actual = palette.yellowOrNull(i)
                assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
            }
        }
    }

    @Test
    fun `should save islands yellow colors to ThemeColorPalette`() {
        val colorName = "Yellow"
        val expectedIslandColors = createIslandThemeColors(colorName, colorCount)

        inIslandsTheme {
            withUiDefaults(expectedIslandColors) {
                val palette = ThemeColorPalette.readFromLaF()
                for (i in 10..colorCount * 10 step 10) {
                    val expected =
                        expectedIslandColors.getValue("ColorPalette.${colorName.lowercase()}-$i").toComposeColor()
                    val actual = palette.yellowOrNull(i)
                    assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
                }
            }
        }
    }

    @Test
    fun `should save orange colors to ThemeColorPalette`() {
        val colorName = "Orange"
        val expectedColors = createNonIslandThemeColors(colorName, colorCount)

        withUiDefaults(expectedColors) {
            val palette = ThemeColorPalette.readFromLaF()
            for (i in 1..colorCount) {
                val expected = expectedColors.getValue("ColorPalette.$colorName$i").toComposeColor()
                val actual = palette.orangeOrNull(i)
                assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
            }
        }
    }

    @Test
    fun `should save islands orange colors to ThemeColorPalette`() {
        val colorName = "Orange"
        val expectedIslandColors = createIslandThemeColors(colorName, colorCount)

        inIslandsTheme {
            withUiDefaults(expectedIslandColors) {
                val palette = ThemeColorPalette.readFromLaF()
                for (i in 10..colorCount * 10 step 10) {
                    val expected =
                        expectedIslandColors.getValue("ColorPalette.${colorName.lowercase()}-$i").toComposeColor()
                    val actual = palette.orangeOrNull(i)
                    assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
                }
            }
        }
    }

    @Test
    fun `should save purple colors to ThemeColorPalette`() {
        val colorName = "Purple"
        val expectedColors = createNonIslandThemeColors(colorName, colorCount)

        withUiDefaults(expectedColors) {
            val palette = ThemeColorPalette.readFromLaF()
            for (i in 1..colorCount) {
                val expected = expectedColors.getValue("ColorPalette.$colorName$i").toComposeColor()
                val actual = palette.purpleOrNull(i)
                assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
            }
        }
    }

    @Test
    fun `should save islands purple colors to ThemeColorPalette`() {
        val colorName = "Purple"
        val expectedIslandColors = createIslandThemeColors(colorName, colorCount)

        inIslandsTheme {
            withUiDefaults(expectedIslandColors) {
                val palette = ThemeColorPalette.readFromLaF()
                for (i in 10..colorCount * 10 step 10) {
                    val expected =
                        expectedIslandColors.getValue("ColorPalette.${colorName.lowercase()}-$i").toComposeColor()
                    val actual = palette.purpleOrNull(i)
                    assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
                }
            }
        }
    }

    @Test
    fun `should save teal colors to ThemeColorPalette`() {
        val colorName = "Teal"
        val expectedColors = createNonIslandThemeColors(colorName, colorCount)

        withUiDefaults(expectedColors) {
            val palette = ThemeColorPalette.readFromLaF()
            for (i in 1..colorCount) {
                val expected = expectedColors.getValue("ColorPalette.$colorName$i").toComposeColor()
                val actual = palette.tealOrNull(i)
                assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
            }
        }
    }

    @Test
    fun `should save islands teal colors to ThemeColorPalette`() {
        val colorName = "Teal"
        val expectedIslandColors = createIslandThemeColors(colorName, colorCount)

        inIslandsTheme {
            withUiDefaults(expectedIslandColors) {
                val palette = ThemeColorPalette.readFromLaF()
                for (i in 10..colorCount * 10 step 10) {
                    val expected =
                        expectedIslandColors.getValue("ColorPalette.${colorName.lowercase()}-$i").toComposeColor()
                    val actual = palette.tealOrNull(i)
                    assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
                }
            }
        }
    }

    @Test
    fun `should save windows popup border color to ThemeColorPalette`() {
        val windowsPopupBorder = Color(100, 100, 100)

        withUiDefaults(mapOf("ColorPalette.windowsPopupBorder" to windowsPopupBorder)) {
            val palette = ThemeColorPalette.readFromLaF()
            assertEquals(windowsPopupBorder.toComposeColor(), palette.windowsPopupBorder)
        }
    }

    private fun createNonIslandThemeColors(colorName: String, count: Int): Map<String, Color> {
        val expectedColors = mutableMapOf<String, Color>()
        for (i in 1..count) {
            val color =
                Color(
                    (colorName.hashCode() and 0xFF0000) shr 16,
                    (colorName.hashCode() and 0x00FF00) shr 8,
                    (i * 10) % 256,
                )
            val key = "ColorPalette.$colorName$i"
            expectedColors[key] = color
        }

        return expectedColors
    }

    private fun createIslandThemeColors(colorName: String, count: Int): Map<String, Color> {
        val expectedColors = mutableMapOf<String, Color>()
        for (i in 10..count * 10 step 10) {
            val color =
                Color(
                    (colorName.hashCode() and 0xFF0000) shr 16,
                    (colorName.hashCode() and 0x00FF00) shr 8,
                    (i * 10) % 256,
                )
            val key = "ColorPalette.${colorName.lowercase()}-$i"
            expectedColors[key] = color
        }

        return expectedColors
    }

    private fun withUiDefaults(entries: Map<String, Any>, block: () -> Unit) {
        val defaults = UIManager.getDefaults()
        // fill the defaults with the provided entries
        entries.forEach { (k, v) -> defaults[k] = v }
        try {
            block()
        } finally {
            // remove the entries from the defaults
            entries.keys.forEach { defaults.remove(it) }
        }
    }

    private fun inIslandsTheme(block: () -> Unit) {
        // set islands theme
        IslandsState.setEnabled(true, false)
        try {
            block()
        } finally {
            // unset islands theme
            IslandsState.setEnabled(false, false)
        }
    }
}
