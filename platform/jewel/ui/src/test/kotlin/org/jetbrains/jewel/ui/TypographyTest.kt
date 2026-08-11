package org.jetbrains.jewel.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.BasicJewelUiTest
import org.junit.Assert.assertEquals
import org.junit.Test

public class TypographyTest : BasicJewelUiTest() {
    @Test
    public fun `unspecified default text style request preserves base style values`() {
        val typography = FakeTypography(labelStyle = TextStyle(fontSize = 12.sp, lineHeight = 18.sp))
        lateinit var textStyle: TextStyle

        runComposeTest({ textStyle = typography.rememberDefaultTextStyle() }) { waitForIdle() }

        assertEquals(12.sp, textStyle.fontSize)
        assertEquals(18.sp, textStyle.lineHeight)
    }

    @Test
    public fun `sp default text style request scales line height`() {
        val typography = FakeTypography(labelStyle = TextStyle(fontSize = 12.sp, lineHeight = 18.sp))
        lateinit var textStyle: TextStyle

        runComposeTest({ textStyle = typography.rememberDefaultTextStyle(fontSize = 16.sp) }) { waitForIdle() }

        assertEquals(16.sp, textStyle.fontSize)
        assertEquals(24.sp, textStyle.lineHeight)
    }

    @Test
    public fun `em default text style request keeps the base line height ratio`() {
        val typography = FakeTypography(labelStyle = TextStyle(fontSize = 12.sp, lineHeight = 18.sp))
        lateinit var textStyle: TextStyle

        runComposeTest({ textStyle = typography.rememberDefaultTextStyle(fontSize = 1.em) }) { waitForIdle() }

        // Line height in em is a multiplier of the resolved font size, so preserve the base 18.sp / 12.sp ratio.
        assertEquals(1.em, textStyle.fontSize)
        assertEquals(1.5.em, textStyle.lineHeight)
    }

    @Test
    public fun `larger em default text style request keeps the base line height ratio`() {
        val typography = FakeTypography(labelStyle = TextStyle(fontSize = 12.sp, lineHeight = 18.sp))
        lateinit var textStyle: TextStyle

        runComposeTest({ textStyle = typography.rememberDefaultTextStyle(fontSize = 2.em) }) { waitForIdle() }

        // Line height in em is a multiplier of the resolved font size, so preserve the base 18.sp / 12.sp ratio.
        assertEquals(2.em, textStyle.fontSize)
        assertEquals(1.5.em, textStyle.lineHeight)
    }

    @Test
    public fun `font attributes override base style only when requested`() {
        val typography =
            FakeTypography(labelStyle = TextStyle(fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic))
        lateinit var preservedStyle: TextStyle
        lateinit var overriddenStyle: TextStyle

        runComposeTest({
            preservedStyle = typography.rememberDefaultTextStyle()
            overriddenStyle =
                typography.rememberDefaultTextStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Normal)
        }) {
            waitForIdle()
        }

        assertEquals(FontWeight.Light, preservedStyle.fontWeight)
        assertEquals(FontStyle.Italic.value, preservedStyle.fontStyle?.value)
        assertEquals(FontWeight.Bold, overriddenStyle.fontWeight)
        assertEquals(FontStyle.Normal.value, overriddenStyle.fontStyle?.value)
    }

    @Test
    public fun `editor request derives from editor base style`() {
        val typography = FakeTypography(editorStyle = TextStyle(fontSize = 10.sp, lineHeight = 14.sp))
        lateinit var textStyle: TextStyle

        runComposeTest({ textStyle = typography.rememberEditorTextStyle(fontSize = 15.sp) }) { waitForIdle() }

        assertEquals(15.sp, textStyle.fontSize)
        assertEquals(21.sp, textStyle.lineHeight)
    }

    @Test
    public fun `console request derives from console base style`() {
        val typography = FakeTypography(consoleStyle = TextStyle(fontSize = 20.sp, lineHeight = 30.sp))
        lateinit var textStyle: TextStyle

        runComposeTest({ textStyle = typography.rememberConsoleTextStyle(fontSize = 10.sp) }) { waitForIdle() }

        assertEquals(10.sp, textStyle.fontSize)
        assertEquals(15.sp, textStyle.lineHeight)
    }

    private class FakeTypography(
        private val labelStyle: TextStyle = TextStyle.Default,
        private val editorStyle: TextStyle = TextStyle.Default,
        private val consoleStyle: TextStyle = TextStyle.Default,
    ) : Typography {
        override val labelTextStyle: TextStyle
            @Composable get() = labelStyle

        override val labelTextSize
            @Composable get() = labelStyle.fontSize

        override val h0TextStyle: TextStyle
            @Composable get() = labelStyle

        override val h1TextStyle: TextStyle
            @Composable get() = labelStyle

        override val h2TextStyle: TextStyle
            @Composable get() = labelStyle

        override val h3TextStyle: TextStyle
            @Composable get() = labelStyle

        override val h4TextStyle: TextStyle
            @Composable get() = labelStyle

        override val regular: TextStyle
            @Composable get() = labelStyle

        override val medium: TextStyle
            @Composable get() = labelStyle

        override val small: TextStyle
            @Composable get() = labelStyle

        override val editorTextStyle: TextStyle
            @Composable get() = editorStyle

        override val consoleTextStyle: TextStyle
            @Composable get() = consoleStyle
    }
}
