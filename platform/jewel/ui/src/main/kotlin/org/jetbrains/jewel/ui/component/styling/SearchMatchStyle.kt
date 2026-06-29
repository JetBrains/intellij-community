// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/** Combines colors and metrics styling for the search match highlight component. */
@GenerateDataFunctions
public class SearchMatchStyle(
    /** The color tokens for the search match highlight. */
    public val colors: SearchMatchColors,
    /** The size and spacing metrics for the search match highlight. */
    public val metrics: SearchMatchMetrics,
) {
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

    /** Companion object for [SearchMatchStyle]. */
    public companion object
}

/** Holds color tokens for the search match highlight component, including gradient background and foreground colors. */
@GenerateDataFunctions
public class SearchMatchColors(
    /** The start color of the background gradient. */
    public val startBackground: Color,
    /** The end color of the background gradient. */
    public val endBackground: Color,
    /** The foreground (text) color. */
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

    /** Companion object for [SearchMatchColors]. */
    public companion object
}

/** Holds size and spacing metrics for the search match highlight component, including corner size and padding. */
@GenerateDataFunctions
public class SearchMatchMetrics(
    /** The corner radius of the highlight shape. */
    public val cornerSize: CornerSize,
    /** The padding applied inside the highlight. */
    public val padding: PaddingValues,
) {
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

    /** Companion object for [SearchMatchMetrics]. */
    public companion object
}

/** CompositionLocal providing the current [SearchMatchStyle]. */
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
