// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/** Combines [PopupAdColors] and [PopupAdMetrics] to define the full styling for a popup ad component. */
@Stable
@GenerateDataFunctions
public class PopupAdStyle(
    /** The color tokens for the popup ad component. */
    public val colors: PopupAdColors,
    /** The size and spacing metrics for the popup ad component. */
    public val metrics: PopupAdMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PopupAdStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "PopupAdStyle(colors=$colors, metrics=$metrics)"

    /** Companion object for [PopupAdStyle]. */
    public companion object
}

/** Holds the color tokens for the popup ad component, namely its background color. */
@Immutable
@GenerateDataFunctions
public class PopupAdColors(
    /** The background color of the popup ad. */
    public val background: Color
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PopupAdColors

        if (background != other.background) return false

        return true
    }

    override fun hashCode(): Int = background.hashCode()

    override fun toString(): String = "PopupAdColors(background=$background)"

    /** Companion object for [PopupAdColors]. */
    public companion object
}

/** Holds size and spacing metrics for the popup ad component, including padding and minimum height. */
@Stable
@GenerateDataFunctions
public class PopupAdMetrics(
    /** The padding applied around the popup ad content. */
    public val padding: PaddingValues,
    /** The minimum height of the popup ad. */
    public val minHeight: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PopupAdMetrics

        if (padding != other.padding) return false
        if (minHeight != other.minHeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = padding.hashCode()
        result = 31 * result + minHeight.hashCode()
        return result
    }

    override fun toString(): String = "PopupAdMetrics(padding=$padding, minHeight=$minHeight)"

    /** Companion object for [PopupAdMetrics]. */
    public companion object
}

/** CompositionLocal providing the current [PopupAdStyle]. */
public val LocalPopupAdStyle: ProvidableCompositionLocal<PopupAdStyle> = staticCompositionLocalOf {
    error("No PopupAdStyle provided. Have you forgotten the theme?")
}

/** Creating a fallback style for compatibility with older versions. */
internal fun fallbackPopupAdStyle() =
    PopupAdStyle(
        colors = PopupAdColors(background = Color(0xFFF2F2F2)),
        metrics = PopupAdMetrics(padding = PaddingValues(horizontal = 20.dp, vertical = 6.dp), minHeight = 20.dp),
    )
