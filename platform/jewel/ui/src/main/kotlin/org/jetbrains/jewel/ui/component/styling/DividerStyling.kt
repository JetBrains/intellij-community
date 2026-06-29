package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/** Combines the color and metrics styling for a divider component. */
@Immutable
@GenerateDataFunctions
public class DividerStyle(
    /** The color of the divider line. */
    public val color: Color,
    /** The size and spacing metrics for the divider. */
    public val metrics: DividerMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DividerStyle

        if (color != other.color) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = color.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "DividerStyle(color=$color, metrics=$metrics)"

    /** Companion object for [DividerStyle]. */
    public companion object
}

/** Holds size and spacing metrics for the divider component. */
@Immutable
@GenerateDataFunctions
public class DividerMetrics(
    /** The thickness of the divider line. */
    public val thickness: Dp,
    /** The indent applied at the start of the divider. */
    public val startIndent: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DividerMetrics

        if (thickness != other.thickness) return false
        if (startIndent != other.startIndent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = thickness.hashCode()
        result = 31 * result + startIndent.hashCode()
        return result
    }

    override fun toString(): String = "DividerMetrics(thickness=$thickness, startIndent=$startIndent)"

    /** Companion object for [DividerMetrics]. */
    public companion object {
        /** Returns a [DividerMetrics] instance with default thickness and start indent values. */
        public fun defaults(thickness: Dp = 1.dp, startIndent: Dp = 0.dp): DividerMetrics =
            DividerMetrics(thickness, startIndent)
    }
}

/** CompositionLocal used to provide the current [DividerStyle] down the composition tree. */
public val LocalDividerStyle: ProvidableCompositionLocal<DividerStyle> = staticCompositionLocalOf {
    error("No DividerStyle provided. Have you forgotten the theme?")
}
