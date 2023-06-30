package org.jetbrains.jewel.styling

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
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.ChipState

@Stable
interface ChipStyle {

    val colors: ChipColors
    val metrics: ChipMetrics
}

@Immutable
interface ChipColors {

    val background: Brush
    val backgroundDisabled: Brush
    val backgroundFocused: Brush
    val backgroundPressed: Brush
    val backgroundHovered: Brush

    @Composable
    fun backgroundFor(state: ChipState) = rememberUpdatedState(
        state.chooseValue(
            normal = background,
            disabled = backgroundDisabled,
            focused = backgroundFocused,
            pressed = backgroundPressed,
            hovered = backgroundHovered
        )
    )

    val content: Color
    val contentDisabled: Color
    val contentFocused: Color
    val contentPressed: Color
    val contentHovered: Color

    @Composable
    fun contentFor(state: ChipState) = rememberUpdatedState(
        state.chooseValue(
            normal = content,
            disabled = contentDisabled,
            focused = contentFocused,
            pressed = contentPressed,
            hovered = contentHovered
        )
    )

    val border: Color
    val borderDisabled: Color
    val borderFocused: Color
    val borderPressed: Color
    val borderHovered: Color

    @Composable
    fun borderFor(state: ChipState) = rememberUpdatedState(
        state.chooseValue(
            normal = border,
            disabled = borderDisabled,
            focused = borderFocused,
            pressed = borderPressed,
            hovered = borderHovered
        )
    )
}

@Stable
interface ChipMetrics {

    val minSize: DpSize
    val cornerSize: CornerSize
    val padding: PaddingValues
    val borderWidth: Dp
}

val LocalChipStyle = staticCompositionLocalOf<ChipStyle> {
    error("No ChipStyle provided")
}
