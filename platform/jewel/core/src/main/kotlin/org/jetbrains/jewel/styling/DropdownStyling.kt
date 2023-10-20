package org.jetbrains.jewel.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.DropdownState
import org.jetbrains.jewel.GenerateDataFunctions
import org.jetbrains.jewel.painter.PainterProvider

@Stable
@GenerateDataFunctions
class DropdownStyle(
    val colors: DropdownColors,
    val metrics: DropdownMetrics,
    val icons: DropdownIcons,
    val textStyle: TextStyle,
    val menuStyle: MenuStyle,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class DropdownColors(
    val background: Color,
    val backgroundDisabled: Color,
    val backgroundFocused: Color,
    val backgroundPressed: Color,
    val backgroundHovered: Color,
    val content: Color,
    val contentDisabled: Color,
    val contentFocused: Color,
    val contentPressed: Color,
    val contentHovered: Color,
    val border: Color,
    val borderDisabled: Color,
    val borderFocused: Color,
    val borderPressed: Color,
    val borderHovered: Color,
    val iconTint: Color,
    val iconTintDisabled: Color,
    val iconTintFocused: Color,
    val iconTintPressed: Color,
    val iconTintHovered: Color,
) {

    @Composable
    fun backgroundFor(state: DropdownState) = rememberUpdatedState(
        when {
            !state.isEnabled -> backgroundDisabled
            state.isPressed -> backgroundPressed
            state.isHovered -> backgroundHovered
            state.isFocused -> backgroundFocused
            state.isActive -> background
            else -> background
        },
    )

    @Composable
    fun contentFor(state: DropdownState) = rememberUpdatedState(
        state.chooseValue(
            normal = content,
            disabled = contentDisabled,
            focused = contentFocused,
            pressed = contentPressed,
            hovered = contentHovered,
            active = content,
        ),
    )

    @Composable
    fun borderFor(state: DropdownState) = rememberUpdatedState(
        state.chooseValue(
            normal = border,
            disabled = borderDisabled,
            focused = borderFocused,
            pressed = borderPressed,
            hovered = borderHovered,
            active = border,
        ),
    )

    @Composable
    fun iconTintFor(state: DropdownState) = rememberUpdatedState(
        state.chooseValue(
            normal = iconTint,
            disabled = iconTintDisabled,
            focused = iconTintFocused,
            pressed = iconTintPressed,
            hovered = iconTintHovered,
            active = iconTint,
        ),
    )

    companion object
}

@Stable
@GenerateDataFunctions
class DropdownMetrics(
    val arrowMinSize: DpSize,
    val minSize: DpSize,
    val cornerSize: CornerSize,
    val contentPadding: PaddingValues,
    val borderWidth: Dp,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class DropdownIcons(val chevronDown: PainterProvider) {

    companion object
}

val LocalDefaultDropdownStyle = staticCompositionLocalOf<DropdownStyle> {
    error("No DefaultDropdownStyle provided")
}

val LocalUndecoratedDropdownStyle = staticCompositionLocalOf<DropdownStyle> {
    error("No UndecoratedDropdownStyle provided")
}
