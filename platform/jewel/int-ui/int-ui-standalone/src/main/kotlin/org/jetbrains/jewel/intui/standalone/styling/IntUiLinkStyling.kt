package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.IntUiTheme
import org.jetbrains.jewel.intui.standalone.standalonePainterProvider
import org.jetbrains.jewel.painter.PainterProvider
import org.jetbrains.jewel.styling.LinkColors
import org.jetbrains.jewel.styling.LinkIcons
import org.jetbrains.jewel.styling.LinkMetrics
import org.jetbrains.jewel.styling.LinkStyle
import org.jetbrains.jewel.styling.LinkTextStyles

@Immutable
data class IntUiLinkStyle(
    override val colors: IntUiLinkColors,
    override val metrics: IntUiLinkMetrics,
    override val icons: IntUiLinkIcons,
    override val textStyles: IntUiLinkTextStyles,
) : LinkStyle {

    companion object {

        @Composable
        fun light(
            colors: IntUiLinkColors = IntUiLinkColors.light(),
            metrics: IntUiLinkMetrics = IntUiLinkMetrics(),
            icons: IntUiLinkIcons = intUiLinkIcons(),
            textStyles: IntUiLinkTextStyles = IntUiLinkTextStyles.light(),
        ) = IntUiLinkStyle(colors, metrics, icons, textStyles)

        @Composable
        fun dark(
            colors: IntUiLinkColors = IntUiLinkColors.dark(),
            metrics: IntUiLinkMetrics = IntUiLinkMetrics(),
            icons: IntUiLinkIcons = intUiLinkIcons(),
            textStyles: IntUiLinkTextStyles = IntUiLinkTextStyles.dark(),
        ) = IntUiLinkStyle(colors, metrics, icons, textStyles)
    }
}

@Immutable
data class IntUiLinkColors(
    override val content: Color,
    override val contentDisabled: Color,
    override val contentFocused: Color,
    override val contentPressed: Color,
    override val contentHovered: Color,
    override val contentVisited: Color,
) : LinkColors {

    companion object {

        @Composable
        fun light(
            content: Color = IntUiLightTheme.colors.blue(2),
            contentDisabled: Color = IntUiLightTheme.colors.grey(8),
            contentFocused: Color = content,
            contentPressed: Color = content,
            contentHovered: Color = content,
            contentVisited: Color = content,
        ) = IntUiLinkColors(
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            contentVisited,
        )

        @Composable
        fun dark(
            content: Color = IntUiDarkTheme.colors.blue(9),
            contentDisabled: Color = IntUiDarkTheme.colors.grey(7),
            contentFocused: Color = content,
            contentPressed: Color = content,
            contentHovered: Color = content,
            contentVisited: Color = content,
        ) = IntUiLinkColors(
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            contentVisited,
        )
    }
}

@Immutable
data class IntUiLinkMetrics(
    override val focusHaloCornerSize: CornerSize = CornerSize(2.dp),
    override val textIconGap: Dp = 0.dp,
    override val iconSize: DpSize = DpSize(16.dp, 16.dp),
) : LinkMetrics

@Immutable
data class IntUiLinkIcons(
    override val dropdownChevron: PainterProvider,
    override val externalLink: PainterProvider,
) : LinkIcons {

    companion object {

        @Composable
        fun dropdownChevron(
            basePath: String = "expui/general/chevronDown.svg",
        ): PainterProvider = standalonePainterProvider(basePath)

        @Composable
        fun externalLink(
            basePath: String = "expui/ide/externalLink.svg",
        ): PainterProvider = standalonePainterProvider(basePath)
    }
}

@Composable
fun intUiLinkIcons(
    dropdownChevron: PainterProvider = IntUiLinkIcons.dropdownChevron(),
    externalLink: PainterProvider = IntUiLinkIcons.externalLink(),
) = IntUiLinkIcons(dropdownChevron, externalLink)

@Immutable
data class IntUiLinkTextStyles(
    override val normal: TextStyle,
    override val disabled: TextStyle,
    override val focused: TextStyle,
    override val pressed: TextStyle,
    override val hovered: TextStyle,
    override val visited: TextStyle,
) : LinkTextStyles {

    companion object {

        @Composable
        fun light(
            normal: TextStyle = IntUiTheme.defaultTextStyle.copy(textDecoration = TextDecoration.Underline),
            disabled: TextStyle = IntUiTheme.defaultTextStyle,
            focused: TextStyle = normal,
            pressed: TextStyle = normal,
            hovered: TextStyle = normal,
            visited: TextStyle = normal,
        ): IntUiLinkTextStyles = IntUiLinkTextStyles(normal, disabled, focused, pressed, hovered, visited)

        @Composable
        fun dark(
            normal: TextStyle = IntUiTheme.defaultTextStyle.copy(textDecoration = TextDecoration.Underline),
            disabled: TextStyle = IntUiTheme.defaultTextStyle,
            focused: TextStyle = normal,
            pressed: TextStyle = normal,
            hovered: TextStyle = normal,
            visited: TextStyle = normal,
        ): IntUiLinkTextStyles = IntUiLinkTextStyles(normal, disabled, focused, pressed, hovered, visited)
    }
}
