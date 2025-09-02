// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@GenerateDataFunctions
public class SearchMatchStyle(public val colors: SearchMatchColors, public val metrics: SearchMatchMetrics) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchMatchStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "SearchMatchStyle(colors=$colors, metrics=$metrics)"

    public companion object
}

@GenerateDataFunctions
public class SearchMatchColors(
    public val startBackground: Color,
    public val endBackground: Color,
    public val foreground: Color,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchMatchColors

        if (startBackground != other.startBackground) return false
        if (endBackground != other.endBackground) return false
        if (foreground != other.foreground) return false

        return true
    }

    override fun hashCode(): Int {
        var result = startBackground.hashCode()
        result = 31 * result + endBackground.hashCode()
        result = 31 * result + foreground.hashCode()
        return result
    }

    override fun toString(): String =
        "SearchMatchColors(startBackground=$startBackground, endBackground=$endBackground, foreground=$foreground)"

    public companion object
}

@GenerateDataFunctions
public class SearchMatchMetrics(public val cornerSize: CornerSize, public val padding: PaddingValues) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchMatchMetrics

        if (cornerSize != other.cornerSize) return false
        if (padding != other.padding) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cornerSize.hashCode()
        result = 31 * result + padding.hashCode()
        return result
    }

    override fun toString(): String = "SearchMatchMetrics(cornerSize=$cornerSize, verticalPadding=$padding)"

    public companion object
}

public val LocalSearchMatchStyle: ProvidableCompositionLocal<SearchMatchStyle> = staticCompositionLocalOf {
    error("No SearchMatchStyle provided. Have you forgotten the theme?")
}

/** Creating a fallback style for compatibility with older versions. */
internal fun fallbackSearchMatchStyle() =
    SearchMatchStyle(
        colors =
            SearchMatchColors(
                startBackground = Color(0xFFFEE6B1),
                endBackground = Color(0xFFFEE6B1),
                foreground = Color(0xFF323232),
            ),
        metrics = SearchMatchMetrics(CornerSize(2.5.dp), PaddingValues(2.dp)),
    )
