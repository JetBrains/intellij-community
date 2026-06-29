package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.SegmentedControlState

/** Combines the [colors] and [metrics] that style a segmented control component. */
public class SegmentedControlStyle(
    /** The color tokens for the segmented control. */
    public val colors: SegmentedControlColors,
    /** The size and spacing metrics for the segmented control. */
    public val metrics: SegmentedControlMetrics,
) {

    /** Companion object for [SegmentedControlStyle]. */
    public companion object
}

/** Holds color tokens for a segmented control component in its various states. */
@Immutable
@GenerateDataFunctions
public class SegmentedControlColors(
    /** The border color in the normal state. */
    public val border: Brush,
    /** The border color when the control is disabled. */
    public val borderDisabled: Brush,
    /** The border color when the control is pressed. */
    public val borderPressed: Brush,
    /** The border color when the control is hovered. */
    public val borderHovered: Brush,
    /** The border color when the control is focused. */
    public val borderFocused: Brush,
) {

    /** Returns a [State] holding the border brush appropriate for the given [state]. */
    @Composable
    public fun borderFor(state: SegmentedControlState): State<Brush> =
        rememberUpdatedState(
            when {
                state.isFocused && state.isEnabled -> borderFocused
                else ->
                    state.chooseValueIgnoreCompat(
                        normal = border,
                        disabled = borderDisabled,
                        pressed = borderPressed,
                        hovered = borderHovered,
                        active = border,
                    )
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SegmentedControlColors

        if (border != other.border) return false
        if (borderDisabled != other.borderDisabled) return false
        if (borderPressed != other.borderPressed) return false
        if (borderHovered != other.borderHovered) return false
        if (borderFocused != other.borderFocused) return false

        return true
    }

    override fun hashCode(): Int {
        var result = border.hashCode()
        result = 31 * result + borderDisabled.hashCode()
        result = 31 * result + borderPressed.hashCode()
        result = 31 * result + borderHovered.hashCode()
        result = 31 * result + borderFocused.hashCode()
        return result
    }

    override fun toString(): String {
        return "SegmentedControlColors(" +
            "border=$border, " +
            "borderDisabled=$borderDisabled, " +
            "borderPressed=$borderPressed, " +
            "borderHovered=$borderHovered, " +
            "borderFocused=$borderFocused" +
            ")"
    }

    /** Companion object for [SegmentedControlColors]. */
    public companion object
}

/** Holds size and spacing metrics for a segmented control component. */
@Stable
@GenerateDataFunctions
public class SegmentedControlMetrics(
    /** The corner radius of the segmented control. */
    public val cornerSize: CornerSize,
    /** The width of the control border. */
    public val borderWidth: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SegmentedControlMetrics

        if (cornerSize != other.cornerSize) return false
        if (borderWidth != other.borderWidth) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cornerSize.hashCode()
        result = 31 * result + borderWidth.hashCode()
        return result
    }

    override fun toString(): String = "SegmentedControlMetrics(cornerSize=$cornerSize, borderWidth=$borderWidth)"

    /** Companion object for [SegmentedControlMetrics]. */
    public companion object
}

@Composable
private fun <T> SegmentedControlState.chooseValueIgnoreCompat(
    normal: T,
    disabled: T,
    pressed: T,
    hovered: T,
    active: T,
): T =
    when {
        !isEnabled -> disabled
        isPressed -> pressed
        isHovered -> hovered
        isActive -> active
        else -> normal
    }

/** CompositionLocal that provides the current [SegmentedControlStyle] to segmented control composables. */
public val LocalSegmentedControlStyle: ProvidableCompositionLocal<SegmentedControlStyle> = staticCompositionLocalOf {
    error("No LocalSegmentedControlStyle provided. Have you forgotten the theme?")
}
