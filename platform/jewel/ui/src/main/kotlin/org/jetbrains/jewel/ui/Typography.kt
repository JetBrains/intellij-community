// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * A quick way to get text styles derived from
 * [the default `TextStyle`][org.jetbrains.jewel.foundation.theme.JewelTheme.defaultTextStyle].
 *
 * These match the functionality provided by `JBFont` in the IntelliJ Platform.
 */
public interface Typography {
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

public val LocalTypography: ProvidableCompositionLocal<Typography> = staticCompositionLocalOf {
    error("No Typography provided. Have you forgotten the theme?")
}

public val JewelTheme.Companion.typography: Typography
    @Composable get() = LocalTypography.current
