package org.jetbrains.jewel.window.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.window.DecoratedWindowState

/** Defines the overall visual style of a decorated window, combining its colors and metrics. */
@Immutable
@GenerateDataFunctions
public class DecoratedWindowStyle(
    /** The colors used for the decorated window frame. */
    public val colors: DecoratedWindowColors,
    /** The layout metrics for the decorated window frame. */
    public val metrics: DecoratedWindowMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecoratedWindowStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "DecoratedWindowStyle(colors=$colors, metrics=$metrics)"

    /** Companion object for [DecoratedWindowStyle]. */
    public companion object
}

/** Holds the border colors used for the decorated window frame in active and inactive states. */
@Immutable
@GenerateDataFunctions
public class DecoratedWindowColors(
    /** The border color when the window is active. */
    public val border: Color,
    /** The border color when the window is inactive. */
    public val borderInactive: Color,
) {
    /**
     * Returns a [State] holding the border color appropriate for the given [state]: the inactive border color when the
     * window is not active, or the active border color otherwise.
     *
     * @param state The current [DecoratedWindowState] of the window.
     */
    @Composable
    public fun borderFor(state: DecoratedWindowState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isActive -> borderInactive
                else -> border
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecoratedWindowColors

        if (border != other.border) return false
        if (borderInactive != other.borderInactive) return false

        return true
    }

    override fun hashCode(): Int {
        var result = border.hashCode()
        result = 31 * result + borderInactive.hashCode()
        return result
    }

    override fun toString(): String = "DecoratedWindowColors(border=$border, borderInactive=$borderInactive)"

    /** Companion object for [DecoratedWindowColors]. */
    public companion object
}

/** Holds the layout metrics for the decorated window frame, such as border width. */
@Immutable
@GenerateDataFunctions
public class DecoratedWindowMetrics(
    /** The width of the decorated window border. */
    public val borderWidth: Dp
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecoratedWindowMetrics

        return borderWidth == other.borderWidth
    }

    override fun hashCode(): Int = borderWidth.hashCode()

    override fun toString(): String = "DecoratedWindowMetrics(borderWidth=$borderWidth)"

    /** Companion object for [DecoratedWindowMetrics]. */
    public companion object
}

/** CompositionLocal providing the current [DecoratedWindowStyle]. */
public val LocalDecoratedWindowStyle: ProvidableCompositionLocal<DecoratedWindowStyle> = staticCompositionLocalOf {
    error("No DecoratedWindowStyle provided. Have you forgotten the theme?")
}
