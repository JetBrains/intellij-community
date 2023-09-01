package org.jetbrains.jewel.themes.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.DropdownState
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.styling.DropdownColors
import org.jetbrains.jewel.styling.DropdownIcons
import org.jetbrains.jewel.styling.DropdownMetrics
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.MenuStyle
import org.jetbrains.jewel.styling.ResourcePainterProvider
import org.jetbrains.jewel.styling.StatefulPainterProvider
import org.jetbrains.jewel.themes.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.themes.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme

@Stable
data class IntUiDropdownStyle(
    override val colors: IntUiDropdownColors,
    override val metrics: IntUiDropdownMetrics,
    override val icons: IntUiDropdownIcons,
    override val textStyle: TextStyle,
    override val menuStyle: MenuStyle,
) : DropdownStyle {

    companion object {

        @Composable
        fun light(
            svgLoader: SvgLoader,
            colors: IntUiDropdownColors = IntUiDropdownColors.light(),
            metrics: IntUiDropdownMetrics = IntUiDropdownMetrics(),
            icons: IntUiDropdownIcons = intUiDropdownIcons(svgLoader),
            textStyle: TextStyle = IntUiTheme.defaultLightTextStyle,
            menuStyle: MenuStyle = IntUiMenuStyle.light(svgLoader),
        ) = IntUiDropdownStyle(colors, metrics, icons, textStyle, menuStyle)

        @Composable
        fun dark(
            svgLoader: SvgLoader,
            colors: IntUiDropdownColors = IntUiDropdownColors.dark(),
            metrics: IntUiDropdownMetrics = IntUiDropdownMetrics(),
            icons: IntUiDropdownIcons = intUiDropdownIcons(svgLoader),
            textStyle: TextStyle = IntUiTheme.defaultDarkTextStyle,
            menuStyle: MenuStyle = IntUiMenuStyle.dark(svgLoader),
        ) = IntUiDropdownStyle(colors, metrics, icons, textStyle, menuStyle)
    }
}

@Immutable
data class IntUiDropdownColors(
    override val background: Color,
    override val backgroundDisabled: Color,
    override val backgroundFocused: Color,
    override val backgroundPressed: Color,
    override val backgroundHovered: Color,
    override val content: Color,
    override val contentDisabled: Color,
    override val contentFocused: Color,
    override val contentPressed: Color,
    override val contentHovered: Color,
    override val border: Color,
    override val borderDisabled: Color,
    override val borderFocused: Color,
    override val borderPressed: Color,
    override val borderHovered: Color,
    override val iconTint: Color,
    override val iconTintDisabled: Color,
    override val iconTintFocused: Color,
    override val iconTintPressed: Color,
    override val iconTintHovered: Color,
) : DropdownColors {

    companion object {

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
        ) = IntUiDropdownColors(
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
            borderDisabled: Color = IntUiDarkTheme.colors.grey(11),
            borderFocused: Color = IntUiDarkTheme.colors.blue(6),
            borderPressed: Color = border,
            borderHovered: Color = border,
            iconTint: Color = IntUiDarkTheme.colors.grey(10),
            iconTintDisabled: Color = IntUiDarkTheme.colors.grey(6),
            iconTintFocused: Color = iconTint,
            iconTintPressed: Color = iconTint,
            iconTintHovered: Color = iconTint,
        ) = IntUiDropdownColors(
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
}

@Stable
data class IntUiDropdownMetrics(
    override val arrowMinSize: DpSize = DpSize((23 + 3).dp, (24 + 6).dp),
    override val minSize: DpSize = DpSize((49 + 23 + 6).dp, (24 + 6).dp),
    override val cornerSize: CornerSize = CornerSize(4.dp),
    override val contentPadding: PaddingValues = PaddingValues(3.dp),
    override val borderWidth: Dp = 1.dp,
) : DropdownMetrics

@Immutable
data class IntUiDropdownIcons(
    override val chevronDown: StatefulPainterProvider<DropdownState>,
) : DropdownIcons {

    companion object {

        @Composable
        fun chevronDown(
            svgLoader: SvgLoader,
            basePath: String = "icons/intui/chevronDown.svg",
        ): StatefulPainterProvider<DropdownState> =
            ResourcePainterProvider(basePath, svgLoader)
    }
}

@Composable
fun intUiDropdownIcons(
    svgLoader: SvgLoader,
    chevronDown: StatefulPainterProvider<DropdownState> = IntUiDropdownIcons.chevronDown(svgLoader),
) =
    IntUiDropdownIcons(chevronDown)
