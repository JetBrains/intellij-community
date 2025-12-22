// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Stable
@GenerateDataFunctions
public class PopupAdTextStyle(
    public val colors: PopupAdTextColors,
    public val metrics: PopupAdTextMetrics,
    public val textStyle: TextStyle,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PopupAdTextStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (textStyle != other.textStyle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + textStyle.hashCode()
        return result
    }

    override fun toString(): String = "PopupAdTextStyle(colors=$colors, metrics=$metrics, textStyle=$textStyle)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class PopupAdTextColors(public val foreground: Color, public val background: Color) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PopupAdTextColors

        if (foreground != other.foreground) return false
        if (background != other.background) return false

        return true
    }

    override fun hashCode(): Int {
        var result = foreground.hashCode()
        result = 31 * result + background.hashCode()
        return result
    }

    override fun toString(): String = "PopupAdTextColors(foreground=$foreground, background=$background)"

    public companion object
}

@Stable
@GenerateDataFunctions
public class PopupAdTextMetrics(
    public val padding: PaddingValues,
    public val minHeight: Dp,
    public val spacerHeight: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PopupAdTextMetrics

        if (padding != other.padding) return false
        if (minHeight != other.minHeight) return false
        if (spacerHeight != other.spacerHeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = padding.hashCode()
        result = 31 * result + minHeight.hashCode()
        result = 31 * result + spacerHeight.hashCode()
        return result
    }

    override fun toString(): String =
        "PopupAdTextMetrics(padding=$padding, minHeight=$minHeight, spacerHeight=$spacerHeight)"

    public companion object
}

public val LocalPopupAdTextStyle: ProvidableCompositionLocal<PopupAdTextStyle> = staticCompositionLocalOf {
    error("No PopupAdTextStyle provided. Have you forgotten the theme?")
}
