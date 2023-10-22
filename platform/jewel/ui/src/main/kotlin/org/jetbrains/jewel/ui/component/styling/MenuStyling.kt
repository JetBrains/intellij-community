package org.jetbrains.jewel.ui.component.styling

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
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.MenuItemState
import org.jetbrains.jewel.ui.painter.PainterProvider

@Stable
@GenerateDataFunctions
class MenuStyle(
    val colors: MenuColors,
    val metrics: MenuMetrics,
    val icons: MenuIcons,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class MenuColors(
    val background: Color,
    val border: Color,
    val shadow: Color,
    val itemColors: MenuItemColors,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class MenuItemColors(
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
    val iconTint: Color,
    val iconTintDisabled: Color,
    val iconTintFocused: Color,
    val iconTintPressed: Color,
    val iconTintHovered: Color,
    val separator: Color,
) {

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

    companion object
}

@Stable
@GenerateDataFunctions
class MenuMetrics(
    val cornerSize: CornerSize,
    val menuMargin: PaddingValues,
    val contentPadding: PaddingValues,
    val offset: DpOffset,
    val shadowSize: Dp,
    val borderWidth: Dp,
    val itemMetrics: MenuItemMetrics,
    val submenuMetrics: SubmenuMetrics,
) {

    companion object
}

@Stable
@GenerateDataFunctions
class MenuItemMetrics(
    val selectionCornerSize: CornerSize,
    val outerPadding: PaddingValues,
    val contentPadding: PaddingValues,
    val separatorPadding: PaddingValues,
    val separatorThickness: Dp,
) {

    companion object
}

@Stable
@GenerateDataFunctions
class SubmenuMetrics(val offset: DpOffset) {

    companion object
}

@Immutable
@GenerateDataFunctions
class MenuIcons(val submenuChevron: PainterProvider) {

    companion object
}

val LocalMenuStyle = staticCompositionLocalOf<MenuStyle> {
    error("No MenuStyle provided")
}
