// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * A quick way to get text styles derived from
 * [the default `TextStyle`][org.jetbrains.jewel.foundation.theme.JewelTheme.defaultTextStyle].
 *
 * These match the functionality provided by `JBFont` in the IntelliJ Platform.
 */
public interface Typography {
    /**
     * Remembers the default text style for this typography implementation.
     *
     * @param fontSize The font size to use. If unspecified, the implementation default is used.
     * @param fontWeight The typeface thickness to use. If null, the implementation default is used.
     * @param fontStyle The typeface variant to use. If null, the implementation default is used.
     */
    @Composable
    public fun rememberDefaultTextStyle(
        fontSize: TextUnit = TextUnit.Unspecified,
        fontWeight: FontWeight? = null,
        fontStyle: FontStyle? = null,
    ): TextStyle {
        val baseStyle = labelTextStyle
        return remember(baseStyle, fontSize, fontWeight, fontStyle) {
            baseStyle.withTypographyRequest(fontSize, fontWeight, fontStyle)
        }
    }

    /**
     * Remembers the editor text style for this typography implementation.
     *
     * @param fontSize The font size to use. If unspecified, the implementation default is used.
     * @param fontWeight The typeface thickness to use. If null, the implementation default is used.
     * @param fontStyle The typeface variant to use. If null, the implementation default is used.
     */
    @Composable
    public fun rememberEditorTextStyle(
        fontSize: TextUnit = TextUnit.Unspecified,
        fontWeight: FontWeight? = null,
        fontStyle: FontStyle? = null,
    ): TextStyle {
        val baseStyle = editorTextStyle
        return remember(baseStyle, fontSize, fontWeight, fontStyle) {
            baseStyle.withTypographyRequest(fontSize, fontWeight, fontStyle)
        }
    }

    /**
     * Remembers the console text style for this typography implementation.
     *
     * @param fontSize The font size to use. If unspecified, the implementation default is used.
     * @param fontWeight The typeface thickness to use. If null, the implementation default is used.
     * @param fontStyle The typeface variant to use. If null, the implementation default is used.
     */
    @Composable
    public fun rememberConsoleTextStyle(
        fontSize: TextUnit = TextUnit.Unspecified,
        fontWeight: FontWeight? = null,
        fontStyle: FontStyle? = null,
    ): TextStyle {
        val baseStyle = consoleTextStyle
        return remember(baseStyle, fontSize, fontWeight, fontStyle) {
            baseStyle.withTypographyRequest(fontSize, fontWeight, fontStyle)
        }
    }

    /**
     * The text style to use for labels. Identical to
     * [`JewelTheme.defaultTextStyle`][org.jetbrains.jewel.foundation.theme.JewelTheme.defaultTextStyle].
     */
    @get:Composable public val labelTextStyle: TextStyle

    /**
     * The text size to use for labels. Identical to the size set in
     * [`JewelTheme.defaultTextStyle`][org.jetbrains.jewel.foundation.theme.JewelTheme.defaultTextStyle].
     */
    @get:Composable public val labelTextSize: TextUnit

    /** The text style to use for h0 titles. Derived from [labelTextStyle]. */
    @get:Composable public val h0TextStyle: TextStyle

    /** The text style to use for h1 titles. Derived from [labelTextStyle]. */
    @get:Composable public val h1TextStyle: TextStyle

    /** The text style to use for h2 titles. Derived from [labelTextStyle]. */
    @get:Composable public val h2TextStyle: TextStyle

    /** The text style to use for h3 titles. Derived from [labelTextStyle]. */
    @get:Composable public val h3TextStyle: TextStyle

    /** The text style to use for h4 titles. Derived from [labelTextStyle]. */
    @get:Composable public val h4TextStyle: TextStyle

    /** The regular text style. Same as [labelTextStyle]. */
    @get:Composable public val regular: TextStyle

    /** The medium text style. Can be the same as [labelTextStyle], or derived from it. */
    @get:Composable public val medium: TextStyle

    /** The small text style. Can be the same as [labelTextStyle], or derived from it. */
    @get:Composable public val small: TextStyle

    /** The text style used for code editors. Usually is a monospaced font. */
    @get:Composable public val editorTextStyle: TextStyle

    /** The text style used for code consoles. Usually is a monospaced font. Can be the same as [editorTextStyle]. */
    @get:Composable public val consoleTextStyle: TextStyle
}

private fun TextStyle.withTypographyRequest(
    fontSize: TextUnit,
    fontWeight: FontWeight?,
    fontStyle: FontStyle?,
): TextStyle =
    copy(
        fontSize = fontSize.takeOrElse { this.fontSize },
        fontWeight = fontWeight ?: this.fontWeight,
        fontStyle = fontStyle ?: this.fontStyle,
        lineHeight = lineHeight.scaledFrom(this.fontSize, fontSize),
    )

private fun TextUnit.scaledFrom(baseFontSize: TextUnit, requestedFontSize: TextUnit): TextUnit =
    when {
        requestedFontSize.isUnspecified -> this
        isSp && baseFontSize.isSp && requestedFontSize.isSp -> {
            val scale = requestedFontSize.value / baseFontSize.value
            (value * scale).sp
        }
        isSp && baseFontSize.isSp && requestedFontSize.isEm -> {
            val lineHeightToFontSizeRatio = value / baseFontSize.value
            lineHeightToFontSizeRatio.em
        }
        else -> this
    }

public val LocalTypography: ProvidableCompositionLocal<Typography> = staticCompositionLocalOf {
    error("No Typography provided. Have you forgotten the theme?")
}

public val JewelTheme.Companion.typography: Typography
    @Composable get() = LocalTypography.current
