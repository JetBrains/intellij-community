// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.util

import androidx.compose.ui.graphics.Color
import java.awt.Color as AwtColor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class ColorExtensionsTest {
    // region ================ AwtColor.toRgbaHexString() ================
    @Test
    fun `old AwtColor_toRgbaHexString should format opaque color with hash sign and no alpha component`() {
        val color = AwtColor.RED
        assertEquals("#ff0000", color.toRgbaHexString())
    }

    @Test
    fun `old AwtColor_toRgbaHexString should format transparent color with hash sign and alpha component`() {
        val color = AwtColor(255, 0, 0, 128)
        assertEquals("#ff000080", color.toRgbaHexString())
    }

    @Test
    fun `AwtColor_toRgbaHexString should format opaque color without hash when includeHashSymbol is false`() {
        val color = AwtColor.BLUE
        assertEquals("0000ffff", color.toRgbaHexString(includeHashSymbol = false))
    }

    @Test
    fun `AwtColor_toRgbaHexString should format opaque color with hash when includeHashSymbol is true`() {
        val color = AwtColor.BLUE
        assertEquals("#0000ffff", color.toRgbaHexString(includeHashSymbol = true))
    }

    @Test
    fun `AwtColor_toRgbaHexString should format opaque color with alpha when omitAlphaWhenFullyOpaque is false`() {
        val color = AwtColor.GREEN
        assertEquals("#00ff00ff", color.toRgbaHexString(omitAlphaWhenFullyOpaque = false))
    }

    @Test
    fun `AwtColor_toRgbaHexString should format opaque color without alpha when omitAlphaWhenFullyOpaque is true`() {
        val color = AwtColor.GREEN
        assertEquals("#00ff00", color.toRgbaHexString(omitAlphaWhenFullyOpaque = true))
    }

    @Test
    fun `AwtColor_toRgbaHexString should format non-opaque color without hash when includeHashSymbol is false`() {
        val color = AwtColor(0, 0, 255, 128)
        assertEquals("0000ff80", color.toRgbaHexString(includeHashSymbol = false))
    }

    @Test
    fun `AwtColor_toRgbaHexString should format non-opaque color with hash when includeHashSymbol is true`() {
        val color = AwtColor(0, 0, 255, 128)
        assertEquals("#0000ff80", color.toRgbaHexString(includeHashSymbol = true))
    }

    @Test
    fun `AwtColor_toRgbaHexString should format non-opaque color with alpha when omitAlphaWhenFullyOpaque is false`() {
        val color = AwtColor(0, 0, 255, 128)
        assertEquals("#0000ff80", color.toRgbaHexString(omitAlphaWhenFullyOpaque = false))
    }

    @Test
    fun `AwtColor_toRgbaHexString should format non-opaque color with alpha when omitAlphaWhenFullyOpaque is true`() {
        val color = AwtColor(0, 255, 0, 128)
        assertEquals("#00ff0080", color.toRgbaHexString(omitAlphaWhenFullyOpaque = true))
    }

    // endregion

    // region ================ Color.toRgbaHexString() ================
    @Test
    fun `old Color_toRgbaHexString should format opaque color with hash sign and no alpha component`() {
        val color = Color.Red
        assertEquals("#ff0000", color.toRgbaHexString())
    }

    @Test
    fun `old Color_toRgbaHexString should format transparent color with hash sign and alpha component`() {
        val color = Color(255, 0, 0, 128)
        assertEquals("#ff000080", color.toRgbaHexString())
    }

    @Test
    fun `Color_toRgbaHexString should format opaque color without hash when includeHashSymbol is false`() {
        val color = Color.Blue
        assertEquals("0000ffff", color.toRgbaHexString(includeHashSymbol = false))
    }

    @Test
    fun `Color_toRgbaHexString should format opaque color with hash when includeHashSymbol is true`() {
        val color = Color.Blue
        assertEquals("#0000ffff", color.toRgbaHexString(includeHashSymbol = true))
    }

    @Test
    fun `Color_toRgbaHexString should format opaque color with alpha when omitAlphaWhenFullyOpaque is false`() {
        val color = Color.Green
        assertEquals("#00ff00ff", color.toRgbaHexString(omitAlphaWhenFullyOpaque = false))
    }

    @Test
    fun `Color_toRgbaHexString should format opaque color without alpha when omitAlphaWhenFullyOpaque is true`() {
        val color = Color.Green
        assertEquals("#00ff00", color.toRgbaHexString(omitAlphaWhenFullyOpaque = true))
    }

    @Test
    fun `Color_toRgbaHexString should format non-opaque color without hash when includeHashSymbol is false`() {
        val color = Color(0, 0, 255, 128)
        assertEquals("0000ff80", color.toRgbaHexString(includeHashSymbol = false))
    }

    @Test
    fun `Color_toRgbaHexString should format non-opaque color with hash when includeHashSymbol is true`() {
        val color = Color(0, 0, 255, 128)
        assertEquals("#0000ff80", color.toRgbaHexString(includeHashSymbol = true))
    }

    @Test
    fun `Color_toRgbaHexString should format non-opaque color with alpha when omitAlphaWhenFullyOpaque is false`() {
        val color = Color(0, 0, 255, 128)
        assertEquals("#0000ff80", color.toRgbaHexString(omitAlphaWhenFullyOpaque = false))
    }

    @Test
    fun `Color_toRgbaHexString should format non-opaque color with alpha when omitAlphaWhenFullyOpaque is true`() {
        val color = Color(0, 255, 0, 128)
        assertEquals("#00ff0080", color.toRgbaHexString(omitAlphaWhenFullyOpaque = true))
    }

    // endregion

    // region ================ AwtColor.toArgbHexString() ================
    @Test
    fun `AwtColor_toArgbHexString should format opaque color without hash when includeHashSymbol is false`() {
        val color = AwtColor.BLUE
        assertEquals("ff0000ff", color.toArgbHexString(includeHashSymbol = false))
    }

    @Test
    fun `AwtColor_toArgbHexString should format opaque color with hash when includeHashSymbol is true`() {
        val color = AwtColor.BLUE
        assertEquals("#ff0000ff", color.toArgbHexString(includeHashSymbol = true))
    }

    @Test
    fun `AwtColor_toArgbHexString should format opaque color with alpha when omitAlphaWhenFullyOpaque is false`() {
        val color = AwtColor.GREEN
        assertEquals("#ff00ff00", color.toArgbHexString(omitAlphaWhenFullyOpaque = false))
    }

    @Test
    fun `AwtColor_toArgbHexString should format opaque color without alpha when omitAlphaWhenFullyOpaque is true`() {
        val color = AwtColor.GREEN
        assertEquals("#00ff00", color.toArgbHexString(omitAlphaWhenFullyOpaque = true))
    }

    @Test
    fun `AwtColor_toArgbHexString should format non-opaque color without hash when includeHashSymbol is false`() {
        val color = AwtColor(0, 0, 255, 128)
        assertEquals("800000ff", color.toArgbHexString(includeHashSymbol = false))
    }

    @Test
    fun `AwtColor_toArgbHexString should format non-opaque color with hash when includeHashSymbol is true`() {
        val color = AwtColor(0, 0, 255, 128)
        assertEquals("#800000ff", color.toArgbHexString(includeHashSymbol = true))
    }

    @Test
    fun `AwtColor_toArgbHexString should format non-opaque color with alpha when omitAlphaWhenFullyOpaque is false`() {
        val color = AwtColor(0, 0, 255, 128)
        assertEquals("#800000ff", color.toArgbHexString(omitAlphaWhenFullyOpaque = false))
    }

    @Test
    fun `AwtColor_toArgbHexString should format non-opaque color with alpha when omitAlphaWhenFullyOpaque is true`() {
        val color = AwtColor(0, 255, 0, 128)
        assertEquals("#8000ff00", color.toArgbHexString(omitAlphaWhenFullyOpaque = true))
    }

    // endregion

    // region ================ Color.toArgbHexString() ================
    @Test
    fun `Color_toArgbHexString should format opaque color without hash when includeHashSymbol is false`() {
        val color = Color.Blue
        assertEquals("ff0000ff", color.toArgbHexString(includeHashSymbol = false))
    }

    @Test
    fun `Color_toArgbHexString should format opaque color with hash when includeHashSymbol is true`() {
        val color = Color.Blue
        assertEquals("#ff0000ff", color.toArgbHexString(includeHashSymbol = true))
    }

    @Test
    fun `Color_toArgbHexString should format opaque color with alpha when omitAlphaWhenFullyOpaque is false`() {
        val color = Color.Green
        assertEquals("#ff00ff00", color.toArgbHexString(omitAlphaWhenFullyOpaque = false))
    }

    @Test
    fun `Color_toArgbHexString should format opaque color without alpha when omitAlphaWhenFullyOpaque is true`() {
        val color = Color.Green
        assertEquals("#00ff00", color.toArgbHexString(omitAlphaWhenFullyOpaque = true))
    }

    @Test
    fun `Color_toArgbHexString should format non-opaque color without hash when includeHashSymbol is false`() {
        val color = Color(0, 0, 255, 128)
        assertEquals("800000ff", color.toArgbHexString(includeHashSymbol = false))
    }

    @Test
    fun `Color_toArgbHexString should format non-opaque color with hash when includeHashSymbol is true`() {
        val color = Color(0, 0, 255, 128)
        assertEquals("#800000ff", color.toArgbHexString(includeHashSymbol = true))
    }

    @Test
    fun `Color_toArgbHexString should format non-opaque color with alpha when omitAlphaWhenFullyOpaque is false`() {
        val color = Color(0, 0, 255, 128)
        assertEquals("#800000ff", color.toArgbHexString(omitAlphaWhenFullyOpaque = false))
    }

    @Test
    fun `Color_toArgbHexString should format non-opaque color with alpha when omitAlphaWhenFullyOpaque is true`() {
        val color = Color(0, 255, 0, 128)
        assertEquals("#8000ff00", color.toArgbHexString(omitAlphaWhenFullyOpaque = true))
    }

    // endregion

    // region ================ Color.fromRgbaHexStringOrNull() ================
    @Test
    fun `fromRgbaHexStringOrNull should parse 3-digit hex without hash prefix`() {
        assertEquals(Color.Red, Color.fromRgbaHexStringOrNull("f00"))
    }

    @Test
    fun `fromRgbaHexStringOrNull should parse 4-digit hex without hash prefix`() {
        assertEquals(Color(0xFFFF0000), Color.fromRgbaHexStringOrNull("f00f"))
    }

    @Test
    fun `fromRgbaHexStringOrNull should parse 6-digit hex without hash prefix`() {
        assertEquals(Color.Yellow, Color.fromRgbaHexStringOrNull("ffff00"))
    }

    @Test
    fun `fromRgbaHexStringOrNull should parse 8-digit hex without hash prefix`() {
        assertEquals(Color(0x80FF0000), Color.fromRgbaHexStringOrNull("ff000080"))
    }

    @Test
    fun `fromRgbaHexStringOrNull should parse 3-digit hex with hash prefix`() {
        assertEquals(Color.Red, Color.fromRgbaHexStringOrNull("#f00"))
    }

    @Test
    fun `fromRgbaHexStringOrNull should parse 4-digit hex with hash prefix`() {
        assertEquals(Color(0xFFFF0000), Color.fromRgbaHexStringOrNull("#f00f"))
    }

    @Test
    fun `fromRgbaHexStringOrNull should parse 6-digit hex with hash prefix`() {
        assertEquals(Color.Yellow, Color.fromRgbaHexStringOrNull("#ffff00"))
    }

    @Test
    fun `fromRgbaHexStringOrNull should parse 8-digit hex with hash prefix`() {
        assertEquals(Color(0x80FF0000), Color.fromRgbaHexStringOrNull("#ff000080"))
    }

    @Test
    fun `fromRgbaHexStringOrNull should parse mixed case`() {
        assertEquals(Color(0x80FF00FF), Color.fromRgbaHexStringOrNull("#ff00FF80"))
    }

    @Test
    fun `fromRgbaHexStringOrNull should return null for invalid hex`() {
        assertNull(Color.fromRgbaHexStringOrNull("1"))
        assertNull(Color.fromRgbaHexStringOrNull("12"))
        assertNull(Color.fromRgbaHexStringOrNull("12345"))
        assertNull(Color.fromRgbaHexStringOrNull("1234567"))
        assertNull(Color.fromRgbaHexStringOrNull("123456789"))
        assertNull(Color.fromRgbaHexStringOrNull("gggj"))
        assertNull(Color.fromRgbaHexStringOrNull(""))
        assertNull(Color.fromRgbaHexStringOrNull(" "))
    }

    @Test
    fun `fromRGBAHexStringOrNull should work same as fromRgbaHexStringOrNull`() {
        assertEquals(Color.fromRgbaHexStringOrNull("ff0"), Color.fromRGBAHexStringOrNull("ff0"))
        assertEquals(Color.fromRgbaHexStringOrNull("ff0000"), Color.fromRGBAHexStringOrNull("ff0000"))
        assertEquals(Color.fromRgbaHexStringOrNull("ffff0000"), Color.fromRGBAHexStringOrNull("ffff0000"))
        assertEquals(Color.fromRgbaHexStringOrNull("banana"), Color.fromRGBAHexStringOrNull("banana"))
        assertEquals(Color.fromRgbaHexStringOrNull(""), Color.fromRGBAHexStringOrNull(""))
    }

    // endregion

    // region ================ Color.fromArgbHexStringOrNull() ================
    @Test
    fun `fromArgbHexStringOrNull should parse 3-digit hex without hash prefix`() {
        assertEquals(Color.Red, Color.fromArgbHexStringOrNull("f00"))
    }

    @Test
    fun `fromArgbHexStringOrNull should parse 4-digit hex without hash prefix`() {
        assertEquals(Color(0xFF0000FF), Color.fromArgbHexStringOrNull("f00f"))
    }

    @Test
    fun `fromArgbHexStringOrNull should parse 6-digit hex without hash prefix`() {
        assertEquals(Color.Yellow, Color.fromArgbHexStringOrNull("ffff00"))
    }

    @Test
    fun `fromArgbHexStringOrNull should parse 8-digit hex without hash prefix`() {
        assertEquals(Color(0x80FF0000), Color.fromArgbHexStringOrNull("80ff0000"))
    }

    @Test
    fun `fromArgbHexStringOrNull should parse 3-digit hex with hash prefix`() {
        assertEquals(Color.Red, Color.fromArgbHexStringOrNull("#f00"))
    }

    @Test
    fun `fromArgbHexStringOrNull should parse 4-digit hex with hash prefix`() {
        assertEquals(Color(0xFF0000FF), Color.fromArgbHexStringOrNull("#f00f"))
    }

    @Test
    fun `fromArgbHexStringOrNull should parse 6-digit hex with hash prefix`() {
        assertEquals(Color.Yellow, Color.fromArgbHexStringOrNull("#ffff00"))
    }

    @Test
    fun `fromArgbHexStringOrNull should parse 8-digit hex with hash prefix`() {
        assertEquals(Color(0x80FF0000), Color.fromArgbHexStringOrNull("#80ff0000"))
    }

    @Test
    fun `fromArgbHexStringOrNull should parse mixed case`() {
        assertEquals(Color(0x80FF00FF), Color.fromArgbHexStringOrNull("#80ff00FF"))
    }

    @Test
    fun `fromArgbHexStringOrNull should return null for invalid hex`() {
        assertNull(Color.fromArgbHexStringOrNull("1"))
        assertNull(Color.fromArgbHexStringOrNull("12"))
        assertNull(Color.fromArgbHexStringOrNull("12345"))
        assertNull(Color.fromArgbHexStringOrNull("1234567"))
        assertNull(Color.fromArgbHexStringOrNull("123456789"))
        assertNull(Color.fromArgbHexStringOrNull("gggj"))
        assertNull(Color.fromArgbHexStringOrNull(""))
        assertNull(Color.fromArgbHexStringOrNull(" "))
    }

    // endregion

    // region ================ Color.isDark() ================
    @Test
    fun `isDark should return true for dark colors`() {
        assertTrue(Color.Black.isDark())
        assertTrue(Color.Blue.isDark())
        assertTrue(Color(0xFF8B0000).isDark()) // Dark Red
    }

    @Test
    fun `isDark should return false for light colors`() {
        assertFalse(Color.White.isDark())
        assertFalse(Color.Yellow.isDark())
        assertFalse(Color.Cyan.isDark())
        assertFalse(Color(0xFFADD8E6).isDark()) // Light Blue
    }
    // endregion
}
