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

@Stable
interface DropdownStyle {

    val colors: DropdownColors
    val metrics: DropdownMetrics
    val icons: DropdownIcons
    val textStyle: TextStyle
    val menuStyle: MenuStyle
}

@Immutable
interface DropdownColors {

    val background: Color
    val backgroundDisabled: Color
    val backgroundFocused: Color
    val backgroundPressed: Color
    val backgroundHovered: Color
    val backgroundWarning: Color
    val backgroundError: Color

    @Composable
    fun backgroundFor(state: DropdownState) = rememberUpdatedState(
        state.chooseValueWithOutline(
            normal = background,
            disabled = backgroundDisabled,
            focused = backgroundFocused,
            pressed = backgroundPressed,
            hovered = backgroundHovered,
            warning = backgroundWarning,
            error = backgroundError
        )
    )

    val content: Color
    val contentDisabled: Color
    val contentFocused: Color
    val contentPressed: Color
    val contentHovered: Color
    val contentWarning: Color
    val contentError: Color

    @Composable
    fun contentFor(state: DropdownState) = rememberUpdatedState(
        state.chooseValueWithOutline(
            normal = content,
            disabled = contentDisabled,
            focused = contentFocused,
            pressed = contentPressed,
            hovered = contentHovered,
            warning = contentWarning,
            error = contentError
        )
    )

    val border: Color
    val borderDisabled: Color
    val borderFocused: Color
    val borderPressed: Color
    val borderHovered: Color
    val borderWarning: Color
    val borderError: Color

    @Composable
    fun borderFor(state: DropdownState) = rememberUpdatedState(
        state.chooseValueWithOutline(
            normal = border,
            disabled = borderDisabled,
            focused = borderFocused,
            pressed = borderPressed,
            hovered = borderHovered,
            warning = borderWarning,
            error = borderError
        )
    )

    val iconTint: Color
    val iconTintDisabled: Color
    val iconTintFocused: Color
    val iconTintPressed: Color
    val iconTintHovered: Color
    val iconTintWarning: Color
    val iconTintError: Color

    @Composable
    fun iconTintFor(state: DropdownState) = rememberUpdatedState(
        state.chooseValueWithOutline(
            normal = iconTint,
            disabled = iconTintDisabled,
            focused = iconTintFocused,
            pressed = iconTintPressed,
            hovered = iconTintHovered,
            warning = iconTintWarning,
            error = iconTintError
        )
    )
}

@Stable
interface DropdownMetrics {

    val minSize: DpSize
    val cornerSize: CornerSize
    val contentPadding: PaddingValues
    val borderWidth: Dp
}

@Immutable
interface DropdownIcons {

    val chevronDown: StatefulPainterProvider<DropdownState>
}

val LocalDropdownStyle = staticCompositionLocalOf<DropdownStyle> {
    error("No DropdownStyle provided")
}
