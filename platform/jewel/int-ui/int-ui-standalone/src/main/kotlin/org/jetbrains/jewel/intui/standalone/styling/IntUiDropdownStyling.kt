package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.JewelTheme
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.defaultTextStyle
import org.jetbrains.jewel.intui.standalone.standalonePainterProvider
import org.jetbrains.jewel.painter.PainterProvider
import org.jetbrains.jewel.styling.DropdownColors
import org.jetbrains.jewel.styling.DropdownIcons
import org.jetbrains.jewel.styling.DropdownMetrics
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.MenuStyle

val DropdownStyle.Companion.Default: IntUiDefaultDropdownStyleFactory
    get() = IntUiDefaultDropdownStyleFactory

object IntUiDefaultDropdownStyleFactory {

    @Composable
    fun light(
        colors: DropdownColors = DropdownColors.Default.light(),
        metrics: DropdownMetrics = DropdownMetrics.default(),
        icons: DropdownIcons = DropdownIcons.defaults(),
        textStyle: TextStyle = JewelTheme.defaultTextStyle,
        menuStyle: MenuStyle = MenuStyle.light(),
    ) = DropdownStyle(colors, metrics, icons, textStyle, menuStyle)

    @Composable
    fun dark(
        colors: DropdownColors = DropdownColors.Default.dark(),
        metrics: DropdownMetrics = DropdownMetrics.default(),
        icons: DropdownIcons = DropdownIcons.defaults(),
        textStyle: TextStyle = JewelTheme.defaultTextStyle,
        menuStyle: MenuStyle = MenuStyle.dark(),
    ) = DropdownStyle(colors, metrics, icons, textStyle, menuStyle)
}

val DropdownStyle.Companion.Undecorated: IntUiUndecoratedDropdownStyleFactory
    get() = IntUiUndecoratedDropdownStyleFactory

object IntUiUndecoratedDropdownStyleFactory {

    @Composable
    fun light(
        colors: DropdownColors = DropdownColors.Undecorated.light(),
        metrics: DropdownMetrics = DropdownMetrics.undecorated(),
        icons: DropdownIcons = DropdownIcons.defaults(),
        textStyle: TextStyle = JewelTheme.defaultTextStyle,
        menuStyle: MenuStyle = MenuStyle.light(),
    ) = DropdownStyle(colors, metrics, icons, textStyle, menuStyle)

    @Composable
    fun dark(
        colors: DropdownColors = DropdownColors.Undecorated.dark(),
        metrics: DropdownMetrics = DropdownMetrics.undecorated(),
        icons: DropdownIcons = DropdownIcons.defaults(),
        textStyle: TextStyle = JewelTheme.defaultTextStyle,
        menuStyle: MenuStyle = MenuStyle.dark(),
    ) = DropdownStyle(colors, metrics, icons, textStyle, menuStyle)
}

val DropdownColors.Companion.Default: IntUiDefaultDropdownColorsFactory
    get() = IntUiDefaultDropdownColorsFactory

object IntUiDefaultDropdownColorsFactory {

    @Composable
    fun light(
        background: Color = IntUiLightTheme.colors.grey(14),
        backgroundDisabled: Color = IntUiLightTheme.colors.grey(13),
        backgroundFocused: Color = background,
        backgroundPressed: Color = background,
        backgroundHovered: Color = background,
        content: Color = IntUiLightTheme.colors.grey(1),
        contentDisabled: Color = IntUiLightTheme.colors.grey(8),
        contentFocused: Color = content,
        contentPressed: Color = content,
        contentHovered: Color = content,
        border: Color = IntUiLightTheme.colors.grey(9),
        borderDisabled: Color = IntUiLightTheme.colors.grey(11),
        borderFocused: Color = IntUiLightTheme.colors.blue(4),
        borderPressed: Color = border,
        borderHovered: Color = border,
        iconTint: Color = IntUiLightTheme.colors.grey(7),
        iconTintDisabled: Color = IntUiLightTheme.colors.grey(9),
        iconTintFocused: Color = iconTint,
        iconTintPressed: Color = iconTint,
        iconTintHovered: Color = iconTint,
    ) = DropdownColors(
        background,
        backgroundDisabled,
        backgroundFocused,
        backgroundPressed,
        backgroundHovered,
        content,
        contentDisabled,
        contentFocused,
        contentPressed,
        contentHovered,
        border,
        borderDisabled,
        borderFocused,
        borderPressed,
        borderHovered,
        iconTint,
        iconTintDisabled,
        iconTintFocused,
        iconTintPressed,
        iconTintHovered,
    )

    @Composable
    fun dark(
        background: Color = IntUiDarkTheme.colors.grey(2),
        backgroundDisabled: Color = background,
        backgroundFocused: Color = background,
        backgroundPressed: Color = background,
        backgroundHovered: Color = background,
        content: Color = IntUiDarkTheme.colors.grey(12),
        contentDisabled: Color = IntUiDarkTheme.colors.grey(7),
        contentFocused: Color = content,
        contentPressed: Color = content,
        contentHovered: Color = content,
        border: Color = IntUiDarkTheme.colors.grey(5),
        borderDisabled: Color = IntUiDarkTheme.colors.grey(5),
        borderFocused: Color = IntUiDarkTheme.colors.blue(6),
        borderPressed: Color = border,
        borderHovered: Color = border,
        iconTint: Color = IntUiDarkTheme.colors.grey(10),
        iconTintDisabled: Color = IntUiDarkTheme.colors.grey(6),
        iconTintFocused: Color = iconTint,
        iconTintPressed: Color = iconTint,
        iconTintHovered: Color = iconTint,
    ) = DropdownColors(
        background,
        backgroundDisabled,
        backgroundFocused,
        backgroundPressed,
        backgroundHovered,
        content,
        contentDisabled,
        contentFocused,
        contentPressed,
        contentHovered,
        border,
        borderDisabled,
        borderFocused,
        borderPressed,
        borderHovered,
        iconTint,
        iconTintDisabled,
        iconTintFocused,
        iconTintPressed,
        iconTintHovered,
    )
}

val DropdownColors.Companion.Undecorated: IntUiUndecoratedDropdownColorsFactory
    get() = IntUiUndecoratedDropdownColorsFactory

object IntUiUndecoratedDropdownColorsFactory {

    @Composable
    fun light(
        background: Color = Color.Transparent,
        backgroundDisabled: Color = background,
        backgroundFocused: Color = background,
        backgroundPressed: Color = IntUiLightTheme.colors.grey(14).copy(alpha = 0.1f),
        backgroundHovered: Color = backgroundPressed,
        content: Color = IntUiLightTheme.colors.grey(1),
        contentDisabled: Color = IntUiLightTheme.colors.grey(8),
        contentFocused: Color = content,
        contentPressed: Color = content,
        contentHovered: Color = content,
        iconTint: Color = IntUiLightTheme.colors.grey(7),
        iconTintDisabled: Color = IntUiLightTheme.colors.grey(9),
        iconTintFocused: Color = iconTint,
        iconTintPressed: Color = iconTint,
        iconTintHovered: Color = iconTint,
    ) = DropdownColors(
        background,
        backgroundDisabled,
        backgroundFocused,
        backgroundPressed,
        backgroundHovered,
        content,
        contentDisabled,
        contentFocused,
        contentPressed,
        contentHovered,
        border = Color.Transparent,
        borderDisabled = Color.Transparent,
        borderFocused = Color.Transparent,
        borderPressed = Color.Transparent,
        borderHovered = Color.Transparent,
        iconTint,
        iconTintDisabled,
        iconTintFocused,
        iconTintPressed,
        iconTintHovered,
    )

    @Composable
    fun dark(
        background: Color = Color.Transparent,
        backgroundDisabled: Color = background,
        backgroundFocused: Color = background,
        backgroundPressed: Color = Color(0x0D000000), // Not a palette color
        backgroundHovered: Color = backgroundPressed,
        content: Color = IntUiDarkTheme.colors.grey(12),
        contentDisabled: Color = IntUiDarkTheme.colors.grey(7),
        contentFocused: Color = content,
        contentPressed: Color = content,
        contentHovered: Color = content,
        iconTint: Color = IntUiDarkTheme.colors.grey(10),
        iconTintDisabled: Color = IntUiDarkTheme.colors.grey(6),
        iconTintFocused: Color = iconTint,
        iconTintPressed: Color = iconTint,
        iconTintHovered: Color = iconTint,
    ) = DropdownColors(
        background,
        backgroundDisabled,
        backgroundFocused,
        backgroundPressed,
        backgroundHovered,
        content,
        contentDisabled,
        contentFocused,
        contentPressed,
        contentHovered,
        border = Color.Transparent,
        borderDisabled = Color.Transparent,
        borderFocused = Color.Transparent,
        borderPressed = Color.Transparent,
        borderHovered = Color.Transparent,
        iconTint,
        iconTintDisabled,
        iconTintFocused,
        iconTintPressed,
        iconTintHovered,
    )
}

fun DropdownMetrics.Companion.default(
    arrowMinSize: DpSize = DpSize((23 + 3).dp, 24.dp),
    minSize: DpSize = DpSize((49 + 23 + 6).dp, 24.dp),
    cornerSize: CornerSize = CornerSize(4.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
    borderWidth: Dp = 1.dp,
) = DropdownMetrics(arrowMinSize, minSize, cornerSize, contentPadding, borderWidth)

fun DropdownMetrics.Companion.undecorated(
    arrowMinSize: DpSize = DpSize((23 + 3).dp, 24.dp),
    minSize: DpSize = DpSize((49 + 23 + 6).dp, 24.dp),
    cornerSize: CornerSize = CornerSize(4.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
    borderWidth: Dp = 0.dp,
) = DropdownMetrics(arrowMinSize, minSize, cornerSize, contentPadding, borderWidth)

fun DropdownIcons.Companion.defaults(
    chevronDown: PainterProvider = standalonePainterProvider("expui/general/chevronDown.svg"),
) = DropdownIcons(chevronDown)
