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
import org.jetbrains.jewel.ui.component.ComboBoxState
import org.jetbrains.jewel.ui.icon.IconKey

@Stable
@GenerateDataFunctions
public class ComboBoxStyle(
    public val colors: ComboBoxColors,
    public val metrics: ComboBoxMetrics,
    public val icons: ComboBoxIcons,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class ComboBoxColors(
    public val background: Color,
    public val nonEditableBackground: Color,
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
) {
    @Composable
    public fun backgroundFor(state: ComboBoxState, isEditable: Boolean): State<Color> =
        rememberUpdatedState(
            when {
                !isEditable && state.isEnabled -> nonEditableBackground
                !isEditable && !state.isEnabled -> backgroundDisabled
                state.isFocused && isEditable -> backgroundFocused
                state.isActive && isEditable -> background
                state.isPressed -> backgroundPressed
                state.isHovered -> backgroundHovered
                isEditable && !state.isEnabled -> backgroundDisabled
                isEditable -> background
                else -> background
            }
        )

    @Composable
    public fun contentFor(state: ComboBoxState): State<Color> =
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
    public fun borderFor(state: ComboBoxState): State<Color> =
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

    public companion object
}

@Stable
@GenerateDataFunctions
public class ComboBoxMetrics(
    public val arrowAreaSize: DpSize,
    public val minSize: DpSize,
    public val cornerSize: CornerSize,
    public val contentPadding: PaddingValues,
    public val popupContentPadding: PaddingValues,
    public val borderWidth: Dp,
    public val maxPopupHeight: Dp,
) {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class ComboBoxIcons(public val chevronDown: IconKey) {
    public companion object
}

public val LocalDefaultComboBoxStyle: ProvidableCompositionLocal<ComboBoxStyle> = staticCompositionLocalOf {
    error("No DefaultComboBoxStyle provided. Have you forgotten the theme?")
}

public val LocalUndecoratedComboBoxStyle: ProvidableCompositionLocal<ComboBoxStyle> = staticCompositionLocalOf {
    error("No UndecoratedComboBoxStyle provided. Have you forgotten the theme?")
}
