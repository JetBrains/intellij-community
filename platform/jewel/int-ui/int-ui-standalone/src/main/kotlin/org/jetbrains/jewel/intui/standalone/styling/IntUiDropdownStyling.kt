package org.jetbrains.jewel.intui.standalone.styling

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
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.IntUiTheme
import org.jetbrains.jewel.intui.standalone.standalonePainterProvider
import org.jetbrains.jewel.painter.PainterProvider
import org.jetbrains.jewel.styling.DropdownColors
import org.jetbrains.jewel.styling.DropdownIcons
import org.jetbrains.jewel.styling.DropdownMetrics
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.MenuStyle

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
        fun undecorated(
            colors: IntUiDropdownColors,
            metrics: IntUiDropdownMetrics = IntUiDropdownMetrics(borderWidth = 0.dp),
            icons: IntUiDropdownIcons = intUiDropdownIcons(),
            textStyle: TextStyle = IntUiTheme.defaultTextStyle,
            menuStyle: MenuStyle = IntUiMenuStyle.light(),
        ) = IntUiDropdownStyle(colors, metrics, icons, textStyle, menuStyle)

        @Composable
        fun light(
            colors: IntUiDropdownColors = IntUiDropdownColors.light(),
            metrics: IntUiDropdownMetrics = IntUiDropdownMetrics(),
            icons: IntUiDropdownIcons = intUiDropdownIcons(),
            textStyle: TextStyle = IntUiTheme.defaultTextStyle,
            menuStyle: MenuStyle = IntUiMenuStyle.light(),
        ) = IntUiDropdownStyle(colors, metrics, icons, textStyle, menuStyle)

        @Composable
        fun dark(
            colors: IntUiDropdownColors = IntUiDropdownColors.dark(),
            metrics: IntUiDropdownMetrics = IntUiDropdownMetrics(),
            icons: IntUiDropdownIcons = intUiDropdownIcons(),
            textStyle: TextStyle = IntUiTheme.defaultTextStyle,
            menuStyle: MenuStyle = IntUiMenuStyle.dark(),
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
        fun undecorated(
            backgroundPressed: Color,
            backgroundHovered: Color = backgroundPressed,
            content: Color,
            contentDisabled: Color = content,
            iconTint: Color,
            iconTintDisabled: Color = iconTint,
        ) = IntUiDropdownColors(
            background = Color.Transparent,
            backgroundDisabled = Color.Transparent,
            backgroundFocused = Color.Transparent,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = content,
            contentPressed = content,
            contentHovered = content,
            border = Color.Transparent,
            borderDisabled = Color.Transparent,
            borderFocused = Color.Transparent,
            borderPressed = Color.Transparent,
            borderHovered = Color.Transparent,
            iconTint = iconTint,
            iconTintDisabled = iconTintDisabled,
            iconTintFocused = iconTint,
            iconTintPressed = iconTint,
            iconTintHovered = iconTint,
        )

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
            borderDisabled: Color = IntUiDarkTheme.colors.grey(5),
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
    override val arrowMinSize: DpSize = DpSize((23 + 3).dp, 24.dp),
    override val minSize: DpSize = DpSize((49 + 23 + 6).dp, 24.dp),
    override val cornerSize: CornerSize = CornerSize(4.dp),
    override val contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
    override val borderWidth: Dp = 1.dp,
) : DropdownMetrics

@Immutable
data class IntUiDropdownIcons(
    override val chevronDown: PainterProvider,
) : DropdownIcons {

    companion object {

        @Composable
        fun chevronDown(
            basePath: String = "expui/general/chevronDown.svg",
        ): PainterProvider = standalonePainterProvider(basePath)
    }
}

@Composable
fun intUiDropdownIcons(
    chevronDown: PainterProvider = IntUiDropdownIcons.chevronDown(),
) =
    IntUiDropdownIcons(chevronDown)
