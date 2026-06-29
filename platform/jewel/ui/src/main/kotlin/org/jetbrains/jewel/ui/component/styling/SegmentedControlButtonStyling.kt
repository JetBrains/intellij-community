package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.SegmentedControlButtonState

/** Combines [SegmentedControlButtonColors] and [SegmentedControlButtonMetrics] to style a segmented control button. */
@Stable
@GenerateDataFunctions
public class SegmentedControlButtonStyle(
    /** The color tokens for the segmented control button. */
    public val colors: SegmentedControlButtonColors,
    /** The size and spacing metrics for the segmented control button. */
    public val metrics: SegmentedControlButtonMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SegmentedControlButtonStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "SegmentedControlButtonStyle(colors=$colors, metrics=$metrics)"

    /** Companion object for [SegmentedControlButtonStyle]. */
    public companion object
}

/** Holds color tokens for a segmented control button in its various states. */
@Immutable
@GenerateDataFunctions
public class SegmentedControlButtonColors(
    /** The background brush in the default state. */
    public val background: Brush,
    /** The background brush when the button is pressed. */
    public val backgroundPressed: Brush,
    /** The background brush when the button is hovered. */
    public val backgroundHovered: Brush,
    /** The background brush when the button is selected. */
    public val backgroundSelected: Brush,
    /** The background brush when the button is selected and focused. */
    public val backgroundSelectedFocused: Brush,
    /** The content (foreground) color in the default state. */
    public val content: Color,
    /** The content (foreground) color when the button is disabled. */
    public val contentDisabled: Color,
    /** The border brush in the default state. */
    public val border: Brush,
    /** The border brush when the button is selected. */
    public val borderSelected: Brush,
    /** The border brush when the button is selected and disabled. */
    public val borderSelectedDisabled: Brush,
    /** The border brush when the button is selected and focused. */
    public val borderSelectedFocused: Brush,
) {

    /**
     * Returns a [State] holding the content color appropriate for the given [state].
     *
     * @param state The current [SegmentedControlButtonState].
     */
    @Composable
    public fun contentFor(state: SegmentedControlButtonState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> contentDisabled
                else -> content
            }
        )

    /**
     * Returns a [State] holding the background brush appropriate for the given [state] and focus status.
     *
     * @param state The current [SegmentedControlButtonState].
     * @param isFocused Whether the button (via its containing segmented control) currently has focus, which further
     *   differentiates the selected appearance.
     */
    @Composable
    public fun backgroundFor(state: SegmentedControlButtonState, isFocused: Boolean): State<Brush> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> background
                state.isSelected && isFocused -> backgroundSelectedFocused
                state.isSelected -> backgroundSelected
                state.isPressed -> backgroundPressed
                state.isHovered -> backgroundHovered
                else -> background
            }
        )

    /**
     * Returns a [State] holding the border brush appropriate for the given [state] and focus status.
     *
     * @param state The current [SegmentedControlButtonState].
     * @param isFocused Whether the button (via its containing segmented control) currently has focus, which further
     *   differentiates the selected border appearance.
     */
    @Composable
    public fun borderFor(state: SegmentedControlButtonState, isFocused: Boolean): State<Brush> =
        rememberUpdatedState(
            when {
                !state.isSelected -> border
                !state.isEnabled -> borderSelectedDisabled
                isFocused -> borderSelectedFocused
                else -> borderSelected
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SegmentedControlButtonColors

        if (background != other.background) return false
        if (backgroundPressed != other.backgroundPressed) return false
        if (backgroundHovered != other.backgroundHovered) return false
        if (backgroundSelected != other.backgroundSelected) return false
        if (backgroundSelectedFocused != other.backgroundSelectedFocused) return false
        if (content != other.content) return false
        if (contentDisabled != other.contentDisabled) return false
        if (border != other.border) return false
        if (borderSelected != other.borderSelected) return false
        if (borderSelectedDisabled != other.borderSelectedDisabled) return false
        if (borderSelectedFocused != other.borderSelectedFocused) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + backgroundPressed.hashCode()
        result = 31 * result + backgroundHovered.hashCode()
        result = 31 * result + backgroundSelected.hashCode()
        result = 31 * result + backgroundSelectedFocused.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + contentDisabled.hashCode()
        result = 31 * result + border.hashCode()
        result = 31 * result + borderSelected.hashCode()
        result = 31 * result + borderSelectedDisabled.hashCode()
        result = 31 * result + borderSelectedFocused.hashCode()
        return result
    }

    override fun toString(): String {
        return "SegmentedControlButtonColors(" +
            "background=$background, " +
            "backgroundPressed=$backgroundPressed, " +
            "backgroundHovered=$backgroundHovered, " +
            "backgroundSelected=$backgroundSelected, " +
            "backgroundSelectedFocused=$backgroundSelectedFocused, " +
            "content=$content, " +
            "contentDisabled=$contentDisabled, " +
            "border=$border, " +
            "borderSelected=$borderSelected, " +
            "borderSelectedDisabled=$borderSelectedDisabled, " +
            "borderSelectedFocused=$borderSelectedFocused" +
            ")"
    }

    /** Companion object for [SegmentedControlButtonColors]. */
    public companion object
}

/** Holds size and spacing metrics for a segmented control button. */
@Stable
@GenerateDataFunctions
public class SegmentedControlButtonMetrics(
    /** The corner radius of the segmented control button. */
    public val cornerSize: CornerSize,
    /** The inner padding of the segmented control button. */
    public val segmentedButtonPadding: PaddingValues,
    /** The minimum size of the segmented control button. */
    public val minSize: DpSize,
    /** The width of the button border. */
    public val borderWidth: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SegmentedControlButtonMetrics

        if (cornerSize != other.cornerSize) return false
        if (segmentedButtonPadding != other.segmentedButtonPadding) return false
        if (minSize != other.minSize) return false
        if (borderWidth != other.borderWidth) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cornerSize.hashCode()
        result = 31 * result + segmentedButtonPadding.hashCode()
        result = 31 * result + minSize.hashCode()
        result = 31 * result + borderWidth.hashCode()
        return result
    }

    override fun toString(): String {
        return "SegmentedControlButtonMetrics(" +
            "cornerSize=$cornerSize, " +
            "segmentedButtonPadding=$segmentedButtonPadding, " +
            "minSize=$minSize, " +
            "borderWidth=$borderWidth" +
            ")"
    }

    /** Companion object for [SegmentedControlButtonMetrics]. */
    public companion object
}

@Composable
private fun <T> SegmentedControlButtonState.chooseValueIgnoreCompat(
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

/** CompositionLocal providing the current [SegmentedControlButtonStyle]. */
public val LocalSegmentedControlButtonStyle: ProvidableCompositionLocal<SegmentedControlButtonStyle> =
    staticCompositionLocalOf {
        error("No LocalSegmentedControlButtonStyle provided. Have you forgotten the theme?")
    }
