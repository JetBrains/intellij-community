package org.jetbrains.jewel.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import org.jetbrains.jewel.MenuItemState
import org.jetbrains.jewel.painter.PainterProvider

@Stable
interface MenuStyle {

    val colors: MenuColors
    val metrics: MenuMetrics
    val icons: MenuIcons
}

@Immutable
interface MenuColors {

    val background: Color
    val border: Color
    val shadow: Color
    val itemColors: MenuItemColors
}

@Stable
interface MenuMetrics {

    val cornerSize: CornerSize
    val menuMargin: PaddingValues
    val contentPadding: PaddingValues
    val offset: DpOffset
    val shadowSize: Dp
    val borderWidth: Dp
    val itemMetrics: MenuItemMetrics
    val submenuMetrics: SubmenuMetrics
}

@Stable
interface MenuItemMetrics {

    val selectionCornerSize: CornerSize
    val outerPadding: PaddingValues
    val contentPadding: PaddingValues
    val separatorPadding: PaddingValues
    val separatorThickness: Dp
}

@Stable
interface SubmenuMetrics {

    val offset: DpOffset
}

@Immutable
interface MenuItemColors {

    val background: Color
    val backgroundDisabled: Color
    val backgroundFocused: Color
    val backgroundPressed: Color
    val backgroundHovered: Color

    @Composable
    fun backgroundFor(state: MenuItemState) = rememberUpdatedState(
        state.chooseValue(
            normal = background,
            disabled = backgroundDisabled,
            active = background,
            focused = backgroundFocused,
            pressed = backgroundPressed,
            hovered = backgroundHovered,
        ),
    )

    val content: Color
    val contentDisabled: Color
    val contentFocused: Color
    val contentPressed: Color
    val contentHovered: Color

    @Composable
    fun contentFor(state: MenuItemState) = rememberUpdatedState(
        state.chooseValue(
            normal = content,
            disabled = contentDisabled,
            focused = contentFocused,
            pressed = contentPressed,
            hovered = contentHovered,
            active = content,
        ),
    )

    val iconTint: Color
    val iconTintDisabled: Color
    val iconTintFocused: Color
    val iconTintPressed: Color
    val iconTintHovered: Color

    @Composable
    fun iconTintFor(state: MenuItemState) = rememberUpdatedState(
        state.chooseValue(
            normal = iconTint,
            disabled = iconTintDisabled,
            focused = iconTintFocused,
            pressed = iconTintPressed,
            hovered = iconTintHovered,
            active = iconTint,
        ),
    )

    val separator: Color
}

@Immutable
interface MenuIcons {

    val submenuChevron: PainterProvider
}

val LocalMenuStyle = staticCompositionLocalOf<MenuStyle> {
    error("No MenuStyle provided")
}
