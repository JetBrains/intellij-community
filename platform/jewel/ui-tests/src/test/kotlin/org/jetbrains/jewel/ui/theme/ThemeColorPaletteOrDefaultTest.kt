// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(InternalJewelApi::class)
class ThemeColorPaletteOrDefaultTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `grayOrDefault returns the palette's own value when present`() {
        val ownValue = Color(0xFF123456)
        val palette = palette(gray = listOf(ownValue))
        var result: Color? = null

        rule.setContent { IntUiTheme { result = palette.grayOrDefault(1) } }

        assertEquals(ownValue, result!!)
    }

    @Test
    fun `grayOrDefault falls back to the light default table when missing`() {
        val palette = palette(gray = emptyList())
        var result: Color? = null

        rule.setContent { IntUiTheme(isDark = false) { result = palette.grayOrDefault(1) } }

        assertEquals(DefaultColorPalette.Light.grayOrNull(1), result)
    }

    @Test
    fun `grayOrDefault falls back to the dark default table when missing`() {
        val palette = palette(gray = emptyList())
        var result: Color? = null

        rule.setContent { IntUiTheme(isDark = true) { result = palette.grayOrDefault(1) } }

        assertEquals(DefaultColorPalette.Dark.grayOrNull(1), result)
    }

    @Test
    fun `grayOrDefault falls back to the Islands light default table when missing`() {
        val palette = palette(gray = emptyList(), isIslands = true)
        var result: Color? = null

        rule.setContent { IntUiTheme(isDark = false) { result = palette.grayOrDefault(10) } }

        assertEquals(DefaultColorPalette.IslandsLight.grayOrNull(10), result)
    }

    @Test
    fun `grayOrDefault falls back to the Islands dark default table when missing`() {
        val palette = palette(gray = emptyList(), isIslands = true)
        var result: Color? = null

        rule.setContent { IntUiTheme(isDark = true) { result = palette.grayOrDefault(10) } }

        assertEquals(DefaultColorPalette.IslandsDark.grayOrNull(10), result)
    }

    @Test
    fun `grayOrDefault returns Unspecified when the index is out of bounds everywhere`() {
        val palette = palette(gray = emptyList())
        var result: Color? = null

        rule.setContent { IntUiTheme { result = palette.grayOrDefault(9999) } }

        assertEquals(Color.Unspecified, result!!)
    }

    private fun palette(gray: List<Color>, isIslands: Boolean = false): ThemeColorPalette =
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
