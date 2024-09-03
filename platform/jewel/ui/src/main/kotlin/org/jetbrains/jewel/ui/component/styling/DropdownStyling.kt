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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.DropdownState
import org.jetbrains.jewel.ui.icon.IconKey

@Stable
@GenerateDataFunctions
public class DropdownStyle(
    public val colors: DropdownColors,
    public val metrics: DropdownMetrics,
    public val icons: DropdownIcons,
    public val menuStyle: MenuStyle,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class DropdownColors(
    public val background: Color,
    public val backgroundDisabled: Color,
    public val backgroundFocused: Color,
    public val backgroundPressed: Color,
    public val backgroundHovered: Color,
    public val content: Color,
    public val contentDisabled: Color,
    public val contentFocused: Color,
    public val contentPressed: Color,
    public val contentHovered: Color,
    public val border: Color,
    public val borderDisabled: Color,
    public val borderFocused: Color,
    public val borderPressed: Color,
    public val borderHovered: Color,
    public val iconTint: Color,
    public val iconTintDisabled: Color,
    public val iconTintFocused: Color,
    public val iconTintPressed: Color,
    public val iconTintHovered: Color,
) {
    @Composable
    public fun backgroundFor(state: DropdownState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> backgroundDisabled
                state.isPressed -> backgroundPressed
                state.isHovered -> backgroundHovered
                state.isFocused -> backgroundFocused
                state.isActive -> background
                else -> background
            }
        )

    @Composable
    public fun contentFor(state: DropdownState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = content,
                disabled = contentDisabled,
                focused = contentFocused,
                pressed = contentPressed,
                hovered = contentHovered,
                active = content,
            )
        )

    @Composable
    public fun borderFor(state: DropdownState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = border,
                disabled = borderDisabled,
                focused = borderFocused,
                pressed = borderPressed,
                hovered = borderHovered,
                active = border,
            )
        )

    @Composable
    public fun iconTintFor(state: DropdownState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = iconTint,
                disabled = iconTintDisabled,
                focused = iconTintFocused,
                pressed = iconTintPressed,
                hovered = iconTintHovered,
                active = iconTint,
            )
        )

    public companion object
}

@Stable
@GenerateDataFunctions
public class DropdownMetrics(
    public val arrowMinSize: DpSize,
    public val minSize: DpSize,
    public val cornerSize: CornerSize,
    public val contentPadding: PaddingValues,
    public val borderWidth: Dp,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class DropdownIcons(public val chevronDown: IconKey) {
    public companion object
}

public val LocalDefaultDropdownStyle: ProvidableCompositionLocal<DropdownStyle> = staticCompositionLocalOf {
    error("No DefaultDropdownStyle provided. Have you forgotten the theme?")
}

public val LocalUndecoratedDropdownStyle: ProvidableCompositionLocal<DropdownStyle> = staticCompositionLocalOf {
    error("No UndecoratedDropdownStyle provided. Have you forgotten the theme?")
}
