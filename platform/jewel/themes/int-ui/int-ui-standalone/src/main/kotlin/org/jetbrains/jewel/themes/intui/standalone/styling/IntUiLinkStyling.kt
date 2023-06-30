package org.jetbrains.jewel.themes.intui.standalone.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.LinkState
import org.jetbrains.jewel.styling.LinkColors
import org.jetbrains.jewel.styling.LinkIcons
import org.jetbrains.jewel.styling.LinkMetrics
import org.jetbrains.jewel.styling.LinkStyle
import org.jetbrains.jewel.styling.LinkTextStyles
import org.jetbrains.jewel.styling.ResourcePainterProvider
import org.jetbrains.jewel.themes.intui.core.palette.IntUiDarkPalette
import org.jetbrains.jewel.themes.intui.core.palette.IntUiLightPalette
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme

@Immutable
data class IntUiLinkStyle(
    override val colors: IntUiLinkColors,
    override val metrics: IntUiLinkMetrics,
    override val icons: IntUiLinkIcons,
    override val textStyles: IntUiLinkTextStyles
) : LinkStyle {

    companion object {

        @Composable
        fun light(
            colors: IntUiLinkColors = IntUiLinkColors.light(),
            metrics: IntUiLinkMetrics = IntUiLinkMetrics(),
            icons: IntUiLinkIcons = IntUiLinkIcons(),
            textStyles: IntUiLinkTextStyles = intUiLinkTextStyles()
        ) = IntUiLinkStyle(colors, metrics, icons, textStyles)

        @Composable
        fun dark(
            colors: IntUiLinkColors = IntUiLinkColors.dark(),
            metrics: IntUiLinkMetrics = IntUiLinkMetrics(),
            icons: IntUiLinkIcons = IntUiLinkIcons(),
            textStyles: IntUiLinkTextStyles = intUiLinkTextStyles()
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
    override val iconTint: Color,
    override val iconTintDisabled: Color
) : LinkColors {

    companion object {

        @Composable
        fun light(
            content: Color = IntUiLightPalette.blue(2),
            contentDisabled: Color = IntUiLightPalette.grey(8),
            contentFocused: Color = content,
            contentPressed: Color = content,
            contentHovered: Color = content,
            contentVisited: Color = content,
            iconTint: Color = Color.Unspecified,
            iconTintDisabled: Color = IntUiLightPalette.grey(9)
        ) = IntUiLinkColors(
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            contentVisited,
            iconTint,
            iconTintDisabled
        )

        @Composable
        fun dark(
            content: Color = IntUiDarkPalette.blue(9),
            contentDisabled: Color = IntUiDarkPalette.grey(7),
            contentFocused: Color = content,
            contentPressed: Color = content,
            contentHovered: Color = content,
            contentVisited: Color = content,
            iconTint: Color = Color.Unspecified,
            iconTintDisabled: Color = IntUiDarkPalette.grey(6)
        ) = IntUiLinkColors(
            content,
            contentDisabled,
            contentFocused,
            contentPressed,
            contentHovered,
            contentVisited,
            iconTint,
            iconTintDisabled
        )
    }
}

@Immutable
data class IntUiLinkMetrics(
    override val focusHaloCornerSize: CornerSize = CornerSize(2.dp),
    override val textIconGap: Dp = 0.dp,
    override val iconSize: DpSize = DpSize(16.dp, 16.dp)
) : LinkMetrics

@Immutable
data class IntUiLinkIcons(
    override val dropdownChevron: ResourcePainterProvider<LinkState> = DropdownChevron(),
    override val externalLink: ResourcePainterProvider<LinkState> = ExternalLink()
) : LinkIcons {

    @Immutable
    data class DropdownChevron(
        override val normal: String = "icons/intui/chevronDown.svg",
        override val disabled: String = normal,
        override val focused: String = normal,
        override val pressed: String = normal,
        override val hovered: String = normal
    ) : ResourcePainterProvider<LinkState>()

    @Immutable
    data class ExternalLink(
        override val normal: String = "icons/intui/externalLink.svg",
        override val disabled: String = normal,
        override val focused: String = normal,
        override val pressed: String = normal,
        override val hovered: String = normal
    ) : ResourcePainterProvider<LinkState>()
}

@Immutable
data class IntUiLinkTextStyles(
    override val normal: TextStyle,
    override val disabled: TextStyle,
    override val focused: TextStyle,
    override val pressed: TextStyle,
    override val hovered: TextStyle,
    override val visited: TextStyle
) : LinkTextStyles

@Composable
fun intUiLinkTextStyles(
    normal: TextStyle = IntUiTheme.defaultTextStyle.copy(textDecoration = TextDecoration.Underline),
    disabled: TextStyle = IntUiTheme.defaultTextStyle,
    focused: TextStyle = normal,
    pressed: TextStyle = normal,
    hovered: TextStyle = normal,
    visited: TextStyle = normal
): IntUiLinkTextStyles = IntUiLinkTextStyles(normal, disabled, focused, pressed, hovered, visited)
