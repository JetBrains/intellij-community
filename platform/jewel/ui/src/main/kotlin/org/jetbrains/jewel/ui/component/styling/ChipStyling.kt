package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
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
class ChipStyle(
    val colors: ChipColors,
    val metrics: ChipMetrics,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class ChipColors(
    val background: Brush,
    val backgroundDisabled: Brush,
    val backgroundFocused: Brush,
    val backgroundPressed: Brush,
    val backgroundHovered: Brush,
    val backgroundSelected: Brush,
    val backgroundSelectedDisabled: Brush,
    val backgroundSelectedPressed: Brush,
    val backgroundSelectedFocused: Brush,
    val backgroundSelectedHovered: Brush,
    val content: Color,
    val contentDisabled: Color,
    val contentFocused: Color,
    val contentPressed: Color,
    val contentHovered: Color,
    val contentSelected: Color,
    val contentSelectedDisabled: Color,
    val contentSelectedPressed: Color,
    val contentSelectedFocused: Color,
    val contentSelectedHovered: Color,
    val border: Color,
    val borderDisabled: Color,
    val borderFocused: Color,
    val borderPressed: Color,
    val borderHovered: Color,
    val borderSelected: Color,
    val borderSelectedDisabled: Color,
    val borderSelectedPressed: Color,
    val borderSelectedFocused: Color,
    val borderSelectedHovered: Color,
) {

    @Composable
    fun backgroundFor(state: ChipState) = rememberUpdatedState(
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
        },
    )

    @Composable
    fun contentFor(state: ChipState) = rememberUpdatedState(
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
        },
    )

    @Composable
    fun borderFor(state: ChipState) = rememberUpdatedState(
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
        },
    )

    companion object
}

@Stable
@GenerateDataFunctions
class ChipMetrics(
    val cornerSize: CornerSize,
    val padding: PaddingValues,
    val borderWidth: Dp,
    val borderWidthSelected: Dp,
) {

    companion object
}

val LocalChipStyle = staticCompositionLocalOf<ChipStyle> {
    error("No ChipStyle provided")
}
