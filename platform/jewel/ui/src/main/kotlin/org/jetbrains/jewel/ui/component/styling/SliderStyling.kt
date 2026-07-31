package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.SliderState

/** Combines the [colors], [metrics], and [thumbShape] that define the appearance of a Slider component. */
@Stable
@GenerateDataFunctions
public class SliderStyle(
    /** The color tokens for the slider. */
    public val colors: SliderColors,
    /** The size and spacing metrics for the slider. */
    public val metrics: SliderMetrics,
    /** The shape used for the thumb. */
    public val thumbShape: Shape,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SliderStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (thumbShape != other.thumbShape) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + thumbShape.hashCode()
        return result
    }

    override fun toString(): String = "SliderStyle(colors=$colors, metrics=$metrics, thumbShape=$thumbShape)"

    /** Companion object for [SliderStyle]. */
    public companion object
}

/** Holds color tokens for the Slider component's track, step markers, and thumb in all states. */
@Immutable
@GenerateDataFunctions
public class SliderColors(
    /** The track color when enabled. */
    public val track: Color,
    /** The filled portion of the track color when enabled. */
    public val trackFilled: Color,
    /** The track color when disabled. */
    public val trackDisabled: Color,
    /** The filled portion of the track color when disabled. */
    public val trackFilledDisabled: Color,
    /** The color of the step marker indicators. */
    public val stepMarker: Color,
    /** The thumb fill color in the normal state. */
    public val thumbFill: Color,
    /** The thumb fill color when disabled. */
    public val thumbFillDisabled: Color,
    /** The thumb fill color when focused. */
    public val thumbFillFocused: Color,
    /** The thumb fill color when pressed. */
    public val thumbFillPressed: Color,
    /** The thumb fill color when hovered. */
    public val thumbFillHovered: Color,
    /** The thumb border color in the normal state. */
    public val thumbBorder: Color,
    /** The thumb border color when focused. */
    public val thumbBorderFocused: Color,
    /** The thumb border color when disabled. */
    public val thumbBorderDisabled: Color,
    /** The thumb border color when pressed. */
    public val thumbBorderPressed: Color,
    /** The thumb border color when hovered. */
    public val thumbBorderHovered: Color,
) {
    /**
     * Returns a [State] holding the thumb fill color appropriate for the given [state].
     *
     * @param state The current [SliderState].
     */
    @Composable
    public fun thumbFillFor(state: SliderState): State<Color> =
        rememberUpdatedState(
            state.chooseColor(
                normal = thumbFill,
                disabled = thumbFillDisabled,
                focused = thumbFillFocused,
                pressed = thumbFillPressed,
                hovered = thumbFillHovered,
            )
        )

    /**
     * Returns a [State] holding the thumb border color appropriate for the given [state].
     *
     * @param state The current [SliderState].
     */
    @Composable
    public fun thumbBorderFor(state: SliderState): State<Color> =
        rememberUpdatedState(
            state.chooseColor(
                normal = thumbBorder,
                disabled = thumbBorderDisabled,
                focused = thumbBorderFocused,
                pressed = thumbBorderPressed,
                hovered = thumbBorderHovered,
            )
        )

    @Composable
    private fun SliderState.chooseColor(
        normal: Color,
        disabled: Color,
        pressed: Color,
        hovered: Color,
        focused: Color,
    ) =
        when {
            !isEnabled -> disabled
            isFocused -> focused
            isPressed && !JewelTheme.isSwingCompatMode -> pressed
            isHovered && !JewelTheme.isSwingCompatMode -> hovered
            else -> normal
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SliderColors

        if (track != other.track) return false
        if (trackFilled != other.trackFilled) return false
        if (trackDisabled != other.trackDisabled) return false
        if (trackFilledDisabled != other.trackFilledDisabled) return false
        if (stepMarker != other.stepMarker) return false
        if (thumbFill != other.thumbFill) return false
        if (thumbFillDisabled != other.thumbFillDisabled) return false
        if (thumbFillFocused != other.thumbFillFocused) return false
        if (thumbFillPressed != other.thumbFillPressed) return false
        if (thumbFillHovered != other.thumbFillHovered) return false
        if (thumbBorder != other.thumbBorder) return false
        if (thumbBorderFocused != other.thumbBorderFocused) return false
        if (thumbBorderDisabled != other.thumbBorderDisabled) return false
        if (thumbBorderPressed != other.thumbBorderPressed) return false
        if (thumbBorderHovered != other.thumbBorderHovered) return false

        return true
    }

    override fun hashCode(): Int {
        var result = track.hashCode()
        result = 31 * result + trackFilled.hashCode()
        result = 31 * result + trackDisabled.hashCode()
        result = 31 * result + trackFilledDisabled.hashCode()
        result = 31 * result + stepMarker.hashCode()
        result = 31 * result + thumbFill.hashCode()
        result = 31 * result + thumbFillDisabled.hashCode()
        result = 31 * result + thumbFillFocused.hashCode()
        result = 31 * result + thumbFillPressed.hashCode()
        result = 31 * result + thumbFillHovered.hashCode()
        result = 31 * result + thumbBorder.hashCode()
        result = 31 * result + thumbBorderFocused.hashCode()
        result = 31 * result + thumbBorderDisabled.hashCode()
        result = 31 * result + thumbBorderPressed.hashCode()
        result = 31 * result + thumbBorderHovered.hashCode()
        return result
    }

    override fun toString(): String {
        return "SliderColors(" +
            "track=$track, " +
            "trackFilled=$trackFilled, " +
            "trackDisabled=$trackDisabled, " +
            "trackFilledDisabled=$trackFilledDisabled, " +
            "stepMarker=$stepMarker, " +
            "thumbFill=$thumbFill, " +
            "thumbFillDisabled=$thumbFillDisabled, " +
            "thumbFillFocused=$thumbFillFocused, " +
            "thumbFillPressed=$thumbFillPressed, " +
            "thumbFillHovered=$thumbFillHovered, " +
            "thumbBorder=$thumbBorder, " +
            "thumbBorderFocused=$thumbBorderFocused, " +
            "thumbBorderDisabled=$thumbBorderDisabled, " +
            "thumbBorderPressed=$thumbBorderPressed, " +
            "thumbBorderHovered=$thumbBorderHovered" +
            ")"
    }

    /** Companion object for [SliderColors]. */
    public companion object
}

/** Holds size and spacing metrics for the Slider component's track, thumb, and step line indicators. */
@Immutable
@GenerateDataFunctions
public class SliderMetrics(
    /** The height of the track. */
    public val trackHeight: Dp,
    /** The size of the thumb. */
    public val thumbSize: DpSize,
    /** The width of the thumb border. */
    public val thumbBorderWidth: Dp,
    /** The height of the step line indicators. */
    public val stepLineHeight: Dp,
    /** The width of the step line indicators. */
    public val stepLineWidth: Dp,
    /** The spacing between the track and the step line indicators. */
    public val trackToStepSpacing: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SliderMetrics

        if (trackHeight != other.trackHeight) return false
        if (thumbSize != other.thumbSize) return false
        if (thumbBorderWidth != other.thumbBorderWidth) return false
        if (stepLineHeight != other.stepLineHeight) return false
        if (stepLineWidth != other.stepLineWidth) return false
        if (trackToStepSpacing != other.trackToStepSpacing) return false

        return true
    }

    override fun hashCode(): Int {
        var result = trackHeight.hashCode()
        result = 31 * result + thumbSize.hashCode()
        result = 31 * result + thumbBorderWidth.hashCode()
        result = 31 * result + stepLineHeight.hashCode()
        result = 31 * result + stepLineWidth.hashCode()
        result = 31 * result + trackToStepSpacing.hashCode()
        return result
    }

    override fun toString(): String {
        return "SliderMetrics(" +
            "trackHeight=$trackHeight, " +
            "thumbSize=$thumbSize, " +
            "thumbBorderWidth=$thumbBorderWidth, " +
            "stepLineHeight=$stepLineHeight, " +
            "stepLineWidth=$stepLineWidth, " +
            "trackToStepSpacing=$trackToStepSpacing" +
            ")"
    }

    /** Companion object for [SliderMetrics]. */
    public companion object
}

/** CompositionLocal providing the [SliderStyle] for the current theme. */
public val LocalSliderStyle: ProvidableCompositionLocal<SliderStyle> = staticCompositionLocalOf {
    error("No default SliderStyle provided. Have you forgotten the theme?")
}
