// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

public class ThemeColorPaletteTest {
    @Test
    public fun `grayOrNull returns the color at the given index`() {
        val palette = palette(gray = listOf(Color.Red, Color.Green, Color.Blue))

        assertEquals(Color.Red, palette.grayOrNull(1)!!)
        assertEquals(Color.Blue, palette.grayOrNull(3)!!)
    }

    @Test
    public fun `grayOrNull returns null when the index is out of bounds`() {
        val palette = palette(gray = listOf(Color.Red))

        assertNull(palette.grayOrNull(2))
    }

    @Test
    public fun `grayOrNull treats an index gap as absent, not the next real color`() {
        // Simulates a partial 3rd-party LaF that declares Gray1 and Gray3 but not Gray2 — the gap must be padded
        // with Color.Unspecified (see BridgeThemeColorPalette.readFromLaF), not simply omitted from the list.
        val palette = palette(gray = listOf(Color.Red, Color.Unspecified, Color.Blue))

        assertEquals(Color.Red, palette.grayOrNull(1)!!)
        assertNull(palette.grayOrNull(2))
        assertEquals(Color.Blue, palette.grayOrNull(3)!!)
    }

    @Test
    public fun `grayOrNull treats an index gap as absent for Islands indexing too`() {
        val palette = palette(gray = listOf(Color.Red, Color.Unspecified, Color.Blue), isIslands = true)

        assertEquals(Color.Red, palette.grayOrNull(10)!!)
        assertNull(palette.grayOrNull(20))
        assertEquals(Color.Blue, palette.grayOrNull(30)!!)
    }

    private fun palette(gray: List<Color> = emptyList(), isIslands: Boolean = false): ThemeColorPalette =
        ThemeColorPalette(
            gray = gray,
            blue = emptyList(),
            green = emptyList(),
            red = emptyList(),
            yellow = emptyList(),
            orange = emptyList(),
            purple = emptyList(),
            teal = emptyList(),
            rawMap = emptyMap(),
            isIslands = isIslands,
        )
}
