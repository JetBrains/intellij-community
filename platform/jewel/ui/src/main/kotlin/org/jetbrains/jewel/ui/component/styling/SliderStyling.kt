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

@Stable
@GenerateDataFunctions
public class SliderStyle(
    public val colors: SliderColors,
    public val metrics: SliderMetrics,
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class SliderColors(
    public val track: Color,
    public val trackFilled: Color,
    public val trackDisabled: Color,
    public val trackFilledDisabled: Color,
    public val stepMarker: Color,
    public val thumbFill: Color,
    public val thumbFillDisabled: Color,
    public val thumbFillFocused: Color,
    public val thumbFillPressed: Color,
    public val thumbFillHovered: Color,
    public val thumbBorder: Color,
    public val thumbBorderFocused: Color,
    public val thumbBorderDisabled: Color,
    public val thumbBorderPressed: Color,
    public val thumbBorderHovered: Color,
) {
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class SliderMetrics(
    public val trackHeight: Dp,
    public val thumbSize: DpSize,
    public val thumbBorderWidth: Dp,
    public val stepLineHeight: Dp,
    public val stepLineWidth: Dp,
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

    public companion object
}

public val LocalSliderStyle: ProvidableCompositionLocal<SliderStyle> = staticCompositionLocalOf {
    error("No default SliderStyle provided. Have you forgotten the theme?")
}
