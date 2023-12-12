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
            ),
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
            ),
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

    public companion object
}

public val LocalSliderStyle: ProvidableCompositionLocal<SliderStyle> =
    staticCompositionLocalOf {
        error("No default SliderStyle provided. Have you forgotten the theme?")
    }
