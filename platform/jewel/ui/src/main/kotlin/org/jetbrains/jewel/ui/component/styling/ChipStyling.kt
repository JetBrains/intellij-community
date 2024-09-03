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
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ChipState

@Stable
@GenerateDataFunctions
public class ChipStyle(public val colors: ChipColors, public val metrics: ChipMetrics) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class ChipColors(
    public val background: Brush,
    public val backgroundDisabled: Brush,
    public val backgroundFocused: Brush,
    public val backgroundPressed: Brush,
    public val backgroundHovered: Brush,
    public val backgroundSelected: Brush,
    public val backgroundSelectedDisabled: Brush,
    public val backgroundSelectedPressed: Brush,
    public val backgroundSelectedFocused: Brush,
    public val backgroundSelectedHovered: Brush,
    public val content: Color,
    public val contentDisabled: Color,
    public val contentFocused: Color,
    public val contentPressed: Color,
    public val contentHovered: Color,
    public val contentSelected: Color,
    public val contentSelectedDisabled: Color,
    public val contentSelectedPressed: Color,
    public val contentSelectedFocused: Color,
    public val contentSelectedHovered: Color,
    public val border: Color,
    public val borderDisabled: Color,
    public val borderFocused: Color,
    public val borderPressed: Color,
    public val borderHovered: Color,
    public val borderSelected: Color,
    public val borderSelectedDisabled: Color,
    public val borderSelectedPressed: Color,
    public val borderSelectedFocused: Color,
    public val borderSelectedHovered: Color,
) {
    @Composable
    public fun backgroundFor(state: ChipState): State<Brush> =
        rememberUpdatedState(
            if (state.isSelected) {
                when {
                    !state.isEnabled -> backgroundSelectedDisabled
                    state.isPressed -> backgroundSelectedPressed
                    state.isFocused -> backgroundSelectedFocused
                    state.isHovered -> backgroundSelectedHovered
                    else -> backgroundSelected
                }
            } else {
                when {
                    !state.isEnabled -> backgroundDisabled
                    state.isPressed -> backgroundPressed
                    state.isFocused -> backgroundFocused
                    state.isHovered -> backgroundHovered
                    else -> background
                }
            }
        )

    @Composable
    public fun contentFor(state: ChipState): State<Color> =
        rememberUpdatedState(
            if (state.isSelected) {
                when {
                    !state.isEnabled -> contentSelectedDisabled
                    state.isPressed -> contentSelectedPressed
                    state.isFocused -> contentSelectedFocused
                    state.isHovered -> contentSelectedHovered
                    else -> contentSelected
                }
            } else {
                when {
                    !state.isEnabled -> contentDisabled
                    state.isPressed -> contentPressed
                    state.isFocused -> contentFocused
                    state.isHovered -> contentHovered
                    else -> content
                }
            }
        )

    @Composable
    public fun borderFor(state: ChipState): State<Color> =
        rememberUpdatedState(
            if (state.isSelected) {
                when {
                    !state.isEnabled -> borderSelectedDisabled
                    state.isPressed && !JewelTheme.isSwingCompatMode -> borderSelectedPressed
                    state.isFocused -> borderSelectedFocused
                    state.isHovered && !JewelTheme.isSwingCompatMode -> borderSelectedHovered
                    else -> borderSelected
                }
            } else {
                when {
                    !state.isEnabled -> borderDisabled
                    state.isPressed && !JewelTheme.isSwingCompatMode -> borderPressed
                    state.isFocused -> borderFocused
                    state.isHovered && !JewelTheme.isSwingCompatMode -> borderHovered
                    else -> border
                }
            }
        )

    public companion object
}

@Stable
@GenerateDataFunctions
public class ChipMetrics(
    public val cornerSize: CornerSize,
    public val padding: PaddingValues,
    public val borderWidth: Dp,
    public val borderWidthSelected: Dp,
) {
    public companion object
}

public val LocalChipStyle: ProvidableCompositionLocal<ChipStyle> = staticCompositionLocalOf {
    error("No ChipStyle provided. Have you forgotten the theme?")
}
