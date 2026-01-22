// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import javax.swing.UIManager
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette
import org.junit.Assert.assertEquals
import org.junit.Test

internal class BridgeThemeColorPaletteTest {

    @Test fun `should save gray colors to ThemeColorPalette`() = testColor("Gray")

    @Test fun `should save blue colors to ThemeColorPalette`() = testColor("Blue")

    @Test fun `should save green colors to ThemeColorPalette`() = testColor("Green")

    @Test fun `should save red colors to ThemeColorPalette`() = testColor("Red")

    @Test fun `should save yellow colors to ThemeColorPalette`() = testColor("Yellow")

    @Test fun `should save orange colors to ThemeColorPalette`() = testColor("Orange")

    @Test fun `should save purple colors to ThemeColorPalette`() = testColor("Purple")

    @Test fun `should save teal colors to ThemeColorPalette`() = testColor("Teal")

    @Test
    fun `should save windows popup border color to ThemeColorPalette`() {
        val defaults = UIManager.getDefaults()
        val windowsPopupBorder = java.awt.Color(100, 100, 100)
        defaults["ColorPalette.windowsPopupBorder"] = windowsPopupBorder

        try {
            val palette = ThemeColorPalette.readFromLaF()
            assertEquals(windowsPopupBorder.toComposeColor(), palette.windowsPopupBorder)
        } finally {
            defaults.remove("ColorPalette.windowsPopupBorder")
        }
    }

    @Test
    fun `should save colors with fractional indices to ThemeColorPalette`() {
        val defaults = UIManager.getDefaults()
        val gray1_25 = java.awt.Color(100, 100, 100, 100)
        val gray1_75 = java.awt.Color(101, 101, 101)
        val gray0_5 = java.awt.Color(102, 102, 102)
        defaults["ColorPalette.Gray1.25"] = gray1_25
        defaults["ColorPalette.Gray1.75"] = gray1_75
        defaults["ColorPalette.Gray0.5"] = gray0_5

        try {
            val palette = ThemeColorPalette.readFromLaF()
            assertEquals(
                "Color Gray1.25 is not correct in the palette",
                gray1_25.toComposeColor(),
                palette.grayOrNull(1, 25),
            )
            assertEquals(
                "Color Gray1.75 is not correct in the palette",
                gray1_75.toComposeColor(),
                palette.grayOrNull(1, 75),
            )
            assertEquals(
                "Color Gray0.5 is not correct in the palette",
                gray0_5.toComposeColor(),
                palette.grayOrNull(0, 5),
            )
        } finally {
            defaults.remove("ColorPalette.Gray1.25")
            defaults.remove("ColorPalette.Gray1.75")
            defaults.remove("ColorPalette.Gray0.5")
        }
    }

    private fun testColor(colorName: String, count: Int = 14) {
        val defaults = UIManager.getDefaults()
        val expectedColors = mutableMapOf<String, java.awt.Color>()

        // fill in the defaults and expected results with the same values
        for (i in 1..count) {
            val color =
                java.awt.Color(
                    (colorName.hashCode() and 0xFF0000) shr 16,
                    (colorName.hashCode() and 0x00FF00) shr 8,
                    (i * 10) % 256,
                )
            val key = "ColorPalette.$colorName$i"
            defaults[key] = color
            expectedColors[key] = color
        }

        try {
            // palette is created with the updated defaults
            val palette = ThemeColorPalette.readFromLaF()
            for (i in 1..count) {
                val expected = expectedColors["ColorPalette.$colorName$i"]!!.toComposeColor()
                val actual =
                    when (colorName) {
                        "Gray" -> palette.grayOrNull(i)
                        "Blue" -> palette.blueOrNull(i)
                        "Green" -> palette.greenOrNull(i)
                        "Red" -> palette.redOrNull(i)
                        "Yellow" -> palette.yellowOrNull(i)
                        "Orange" -> palette.orangeOrNull(i)
                        "Purple" -> palette.purpleOrNull(i)
                        "Teal" -> palette.tealOrNull(i)
                        else -> null
                    }
                assertEquals("Color $colorName$i is not correct in the palette", expected, actual)
            }
        } finally {
            for (key in expectedColors.keys) {
                defaults.remove(key)
            }
        }
    }
}
