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
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ButtonState

@Stable
@GenerateDataFunctions
public class ButtonStyle(
    public val colors: ButtonColors,
    public val metrics: ButtonMetrics,
    public val focusOutlineAlignment: Stroke.Alignment,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class ButtonColors(
    public val background: Brush,
    public val backgroundDisabled: Brush,
    public val backgroundFocused: Brush,
    public val backgroundPressed: Brush,
    public val backgroundHovered: Brush,
    public val content: Color,
    public val contentDisabled: Color,
    public val contentFocused: Color,
    public val contentPressed: Color,
    public val contentHovered: Color,
    public val border: Brush,
    public val borderDisabled: Brush,
    public val borderFocused: Brush,
    public val borderPressed: Brush,
    public val borderHovered: Brush,
) {
    @Composable
    public fun backgroundFor(state: ButtonState): State<Brush> =
        rememberUpdatedState(
            state.chooseValue(
                normal = background,
                disabled = backgroundDisabled,
                focused = backgroundFocused,
                pressed = backgroundPressed,
                hovered = backgroundHovered,
                active = background,
            )
        )

    @Composable
    public fun contentFor(state: ButtonState): State<Color> =
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
    public fun borderFor(state: ButtonState): State<Brush> =
        rememberUpdatedState(
            if (JewelTheme.isSwingCompatMode) {
                state.chooseValue(
                    normal = border,
                    disabled = borderDisabled,
                    focused = borderFocused,
                    pressed = borderPressed,
                    hovered = borderHovered,
                    active = border,
                )
            } else {
                when {
                    !state.isEnabled -> borderDisabled
                    state.isFocused -> borderFocused
                    state.isPressed -> borderPressed
                    state.isHovered -> borderHovered
                    else -> border
                }
            }
        )

    public companion object
}

@Stable
@GenerateDataFunctions
public class ButtonMetrics(
    public val cornerSize: CornerSize,
    public val padding: PaddingValues,
    public val minSize: DpSize,
    public val borderWidth: Dp,
    public val focusOutlineExpand: Dp,
) {
    public companion object
}

public val LocalDefaultButtonStyle: ProvidableCompositionLocal<ButtonStyle> = staticCompositionLocalOf {
    error("No default ButtonStyle provided. Have you forgotten the theme?")
}

public val LocalOutlinedButtonStyle: ProvidableCompositionLocal<ButtonStyle> = staticCompositionLocalOf {
    error("No outlined ButtonStyle provided. Have you forgotten the theme?")
}
