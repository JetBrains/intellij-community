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

@Stable
@GenerateDataFunctions
public class SegmentedControlButtonStyle(
    public val colors: SegmentedControlButtonColors,
    public val metrics: SegmentedControlButtonMetrics,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class SegmentedControlButtonColors(
    public val background: Brush,
    public val backgroundPressed: Brush,
    public val backgroundHovered: Brush,
    public val backgroundSelected: Brush,
    public val backgroundSelectedFocused: Brush,
    public val content: Color,
    public val contentDisabled: Color,
    public val border: Brush,
    public val borderSelected: Brush,
    public val borderSelectedDisabled: Brush,
    public val borderSelectedFocused: Brush,
) {

    @Composable
    public fun contentFor(state: SegmentedControlButtonState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> contentDisabled
                else -> content
            }
        )

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

    public companion object
}

@Stable
@GenerateDataFunctions
public class SegmentedControlButtonMetrics(
    public val cornerSize: CornerSize,
    public val segmentedButtonPadding: PaddingValues,
    public val minSize: DpSize,
    public val borderWidth: Dp,
) {

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

public val LocalSegmentedControlButtonStyle: ProvidableCompositionLocal<SegmentedControlButtonStyle> =
    staticCompositionLocalOf {
        error("No LocalSegmentedControlButtonStyle provided. Have you forgotten the theme?")
    }
