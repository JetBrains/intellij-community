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
import androidx.compose.ui.unit.DpOffset
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.MenuItemState
import org.jetbrains.jewel.ui.painter.PainterProvider

@Stable
@GenerateDataFunctions
public class MenuStyle(
    public val isDark: Boolean,
    public val colors: MenuColors,
    public val metrics: MenuMetrics,
    public val icons: MenuIcons,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class MenuColors(
    public val background: Color,
    public val border: Color,
    public val shadow: Color,
    public val itemColors: MenuItemColors,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class MenuItemColors(
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
    public val iconTint: Color,
    public val iconTintDisabled: Color,
    public val iconTintFocused: Color,
    public val iconTintPressed: Color,
    public val iconTintHovered: Color,
    public val keybindingTint: Color,
    public val keybindingTintDisabled: Color,
    public val keybindingTintFocused: Color,
    public val keybindingTintPressed: Color,
    public val keybindingTintHovered: Color,
    public val separator: Color,
) {

    @Composable
    public fun backgroundFor(state: MenuItemState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = background,
                disabled = backgroundDisabled,
                active = background,
                focused = backgroundFocused,
                pressed = backgroundPressed,
                hovered = backgroundHovered,
            ),
        )

    @Composable
    public fun contentFor(state: MenuItemState): State<Color> =
        rememberUpdatedState(
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
    public fun iconTintFor(state: MenuItemState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = iconTint,
                disabled = iconTintDisabled,
                focused = iconTintFocused,
                pressed = iconTintPressed,
                hovered = iconTintHovered,
                active = iconTint,
            ),
        )

    @Composable
    public fun keybindingTintFor(state: MenuItemState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = keybindingTint,
                disabled = keybindingTintDisabled,
                focused = keybindingTintFocused,
                pressed = keybindingTintPressed,
                hovered = keybindingTintHovered,
                active = keybindingTint,
            ),
        )

    public companion object
}

@Stable
@GenerateDataFunctions
public class MenuMetrics(
    public val cornerSize: CornerSize,
    public val menuMargin: PaddingValues,
    public val contentPadding: PaddingValues,
    public val offset: DpOffset,
    public val shadowSize: Dp,
    public val borderWidth: Dp,
    public val itemMetrics: MenuItemMetrics,
    public val submenuMetrics: SubmenuMetrics,
) {

    public companion object
}

@Stable
@GenerateDataFunctions
public class MenuItemMetrics(
    public val selectionCornerSize: CornerSize,
    public val outerPadding: PaddingValues,
    public val contentPadding: PaddingValues,
    public val separatorPadding: PaddingValues,
    public val keybindingsPadding: PaddingValues,
    public val separatorThickness: Dp,
    public val iconSize: Dp,
) {

    public companion object
}

@Stable
@GenerateDataFunctions
public class SubmenuMetrics(public val offset: DpOffset) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class MenuIcons(public val submenuChevron: PainterProvider) {

    public companion object
}

public val LocalMenuStyle: ProvidableCompositionLocal<MenuStyle> =
    staticCompositionLocalOf {
        error("No MenuStyle provided. Have you forgotten the theme?")
    }
