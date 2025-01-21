package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.MenuColors
import org.jetbrains.jewel.ui.component.styling.MenuIcons
import org.jetbrains.jewel.ui.component.styling.MenuItemColors
import org.jetbrains.jewel.ui.component.styling.MenuItemMetrics
import org.jetbrains.jewel.ui.component.styling.MenuMetrics
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.component.styling.SubmenuMetrics
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
public fun MenuStyle.Companion.light(
    colors: MenuColors = MenuColors.light(),
    metrics: MenuMetrics = MenuMetrics.defaults(),
    icons: MenuIcons = MenuIcons.defaults(),
): MenuStyle = MenuStyle(isDark = false, colors, metrics, icons)

@Composable
public fun MenuStyle.Companion.dark(
    colors: MenuColors = MenuColors.dark(),
    metrics: MenuMetrics = MenuMetrics.defaults(),
    icons: MenuIcons = MenuIcons.defaults(),
): MenuStyle = MenuStyle(isDark = true, colors, metrics, icons)

@Composable
public fun MenuColors.Companion.light(
    background: Color = IntUiLightTheme.colors.gray(14),
    border: Color = IntUiLightTheme.colors.gray(9),
    shadow: Color = Color(0x78919191), // Not a palette color
    itemColors: MenuItemColors = MenuItemColors.light(),
): MenuColors = MenuColors(background = background, border = border, shadow = shadow, itemColors = itemColors)

@Composable
public fun MenuColors.Companion.dark(
    background: Color = IntUiDarkTheme.colors.gray(2),
    border: Color = IntUiDarkTheme.colors.gray(3),
    shadow: Color = Color(0x66000000), // Not a palette color
    itemColors: MenuItemColors = MenuItemColors.dark(),
): MenuColors = MenuColors(background = background, border = border, shadow = shadow, itemColors = itemColors)

@Composable
public fun MenuItemColors.Companion.light(
    background: Color = IntUiLightTheme.colors.gray(14),
    backgroundDisabled: Color = IntUiLightTheme.colors.gray(14),
    backgroundFocused: Color = IntUiLightTheme.colors.blue(11),
    backgroundPressed: Color = background,
    backgroundHovered: Color = backgroundFocused,
    content: Color = IntUiLightTheme.colors.gray(1),
    contentDisabled: Color = IntUiLightTheme.colors.gray(8),
    contentFocused: Color = content,
    contentPressed: Color = content,
    contentHovered: Color = content,
    iconTint: Color = IntUiLightTheme.colors.gray(7),
    iconTintDisabled: Color = iconTint,
    iconTintFocused: Color = iconTint,
    iconTintPressed: Color = iconTint,
    iconTintHovered: Color = iconTint,
    keybindingTint: Color = IntUiLightTheme.colors.gray(8),
    keybindingTintDisabled: Color = keybindingTint,
    keybindingTintFocused: Color = IntUiLightTheme.colors.gray(1),
    keybindingTintPressed: Color = keybindingTintFocused,
    keybindingTintHovered: Color = keybindingTintFocused,
    separator: Color = IntUiLightTheme.colors.gray(12),
): MenuItemColors =
    MenuItemColors(
        background = background,
        backgroundDisabled = backgroundDisabled,
        backgroundFocused = backgroundFocused,
        backgroundPressed = backgroundPressed,
        backgroundHovered = backgroundHovered,
        content = content,
        contentDisabled = contentDisabled,
        contentFocused = contentFocused,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
        iconTint = iconTint,
        iconTintDisabled = iconTintDisabled,
        iconTintFocused = iconTintFocused,
        iconTintPressed = iconTintPressed,
        iconTintHovered = iconTintHovered,
        keybindingTint = keybindingTint,
        keybindingTintDisabled = keybindingTintDisabled,
        keybindingTintFocused = keybindingTintFocused,
        keybindingTintPressed = keybindingTintPressed,
        keybindingTintHovered = keybindingTintHovered,
        separator = separator,
    )

@Composable
public fun MenuItemColors.Companion.dark(
    background: Color = IntUiDarkTheme.colors.gray(2),
    backgroundDisabled: Color = IntUiDarkTheme.colors.gray(2),
    backgroundFocused: Color = IntUiDarkTheme.colors.blue(2),
    backgroundPressed: Color = background,
    backgroundHovered: Color = backgroundFocused,
    content: Color = IntUiDarkTheme.colors.gray(12),
    contentDisabled: Color = IntUiDarkTheme.colors.gray(7),
    contentFocused: Color = content,
    contentPressed: Color = content,
    contentHovered: Color = content,
    iconTint: Color = IntUiDarkTheme.colors.gray(10),
    iconTintDisabled: Color = iconTint,
    iconTintFocused: Color = iconTint,
    iconTintPressed: Color = iconTint,
    iconTintHovered: Color = iconTint,
    keybindingTint: Color = IntUiDarkTheme.colors.gray(7),
    keybindingTintDisabled: Color = keybindingTint,
    keybindingTintFocused: Color = IntUiDarkTheme.colors.gray(12),
    keybindingTintPressed: Color = keybindingTintFocused,
    keybindingTintHovered: Color = keybindingTintFocused,
    separator: Color = IntUiDarkTheme.colors.gray(3),
): MenuItemColors =
    MenuItemColors(
        background = background,
        backgroundDisabled = backgroundDisabled,
        backgroundFocused = backgroundFocused,
        backgroundPressed = backgroundPressed,
        backgroundHovered = backgroundHovered,
        content = content,
        contentDisabled = contentDisabled,
        contentFocused = contentFocused,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
        iconTint = iconTint,
        iconTintDisabled = iconTintDisabled,
        iconTintFocused = iconTintFocused,
        iconTintPressed = iconTintPressed,
        iconTintHovered = iconTintHovered,
        separator = separator,
        keybindingTint = keybindingTint,
        keybindingTintDisabled = keybindingTintDisabled,
        keybindingTintFocused = keybindingTintFocused,
        keybindingTintPressed = keybindingTintPressed,
        keybindingTintHovered = keybindingTintHovered,
    )

public fun MenuMetrics.Companion.defaults(
    cornerSize: CornerSize = CornerSize(8.dp),
    menuMargin: PaddingValues = PaddingValues(vertical = 6.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    offset: DpOffset = DpOffset(0.dp, 2.dp),
    shadowSize: Dp = 12.dp,
    borderWidth: Dp = 1.dp,
    itemMetrics: MenuItemMetrics = MenuItemMetrics.defaults(),
    submenuMetrics: SubmenuMetrics = SubmenuMetrics.defaults(),
): MenuMetrics =
    MenuMetrics(cornerSize, menuMargin, contentPadding, offset, shadowSize, borderWidth, itemMetrics, submenuMetrics)

public fun MenuItemMetrics.Companion.defaults(
    selectionCornerSize: CornerSize = CornerSize(0.dp),
    outerPadding: PaddingValues = PaddingValues(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    separatorPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    keybindingsPadding: PaddingValues = PaddingValues(start = 36.dp),
    separatorThickness: Dp = 1.dp,
    separatorHeight: Dp = 9.dp,
    iconSize: Dp = 16.dp,
    minHeight: Dp = 20.dp,
): MenuItemMetrics =
    MenuItemMetrics(
        selectionCornerSize,
        outerPadding,
        contentPadding,
        separatorPadding,
        keybindingsPadding,
        separatorThickness,
        separatorHeight,
        iconSize,
        minHeight,
    )

public fun SubmenuMetrics.Companion.defaults(offset: DpOffset = DpOffset(0.dp, (-8).dp)): SubmenuMetrics =
    SubmenuMetrics(offset)

public fun MenuIcons.Companion.defaults(submenuChevron: IconKey = AllIconsKeys.General.ChevronRight): MenuIcons =
    MenuIcons(submenuChevron)
