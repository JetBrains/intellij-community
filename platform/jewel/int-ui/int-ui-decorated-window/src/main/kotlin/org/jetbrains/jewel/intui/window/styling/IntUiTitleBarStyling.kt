package org.jetbrains.jewel.intui.window.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.styling.IntUiDropdownColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiDropdownMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiDropdownStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiIconButtonColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiIconButtonMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiIconButtonStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiMenuStyle
import org.jetbrains.jewel.intui.window.decoratedWindowPainterProvider
import org.jetbrains.jewel.painter.PainterProvider
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.IconButtonStyle
import org.jetbrains.jewel.styling.MenuStyle
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarIcons
import org.jetbrains.jewel.window.styling.TitleBarMetrics
import org.jetbrains.jewel.window.styling.TitleBarStyle

@Stable
@Immutable
class IntUiTitleBarStyle(
    override val colors: IntUiTitleBarColors,
    override val metrics: IntUiTitleBarMetrics,
    override val icons: TitleBarIcons,
    override val dropdownStyle: DropdownStyle,
    override val iconButtonStyle: IconButtonStyle,
    override val paneButtonStyle: IconButtonStyle,
    override val paneCloseButtonStyle: IconButtonStyle,
) : TitleBarStyle {

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + icons.hashCode()
        result = 31 * result + dropdownStyle.hashCode()
        result = 31 * result + iconButtonStyle.hashCode()
        result = 31 * result + paneButtonStyle.hashCode()
        result = 31 * result + paneCloseButtonStyle.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntUiTitleBarStyle) return false

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (icons != other.icons) return false
        if (dropdownStyle != other.dropdownStyle) return false
        if (iconButtonStyle != other.iconButtonStyle) return false

        return true
    }

    override fun toString(): String = "IntUiTitleBarStyle(colors=$colors, " +
        "metrics=$metrics, " +
        "icons=$icons, " +
        "dropdownStyle=$dropdownStyle, " +
        "iconButtonStyle=$iconButtonStyle, " +
        "paneButtonStyle=$paneButtonStyle, " +
        "paneCloseButtonStyle=$paneCloseButtonStyle)"

    companion object {

        @Composable
        private fun titleBarDropdownStyle(
            content: Color,
            hoverBackground: Color,
            pressBackground: Color,
            menuStyle: MenuStyle,
        ) = IntUiDropdownStyle.undecorated(
            IntUiDropdownColors.undecorated(
                backgroundHovered = hoverBackground,
                backgroundPressed = pressBackground,
                content = content,
                iconTint = Color.Unspecified,
            ),
            metrics = IntUiDropdownMetrics(
                arrowMinSize = DpSize(30.dp, 30.dp),
                cornerSize = CornerSize(6.dp),
                minSize = DpSize((23 + 6).dp, 30.dp),
                contentPadding = PaddingValues(top = 3.dp, bottom = 3.dp, start = 6.dp, end = 0.dp),
                borderWidth = 0.dp,
            ),
            menuStyle = menuStyle,
        )

        @Composable
        private fun titleBarIconButtonStyle(
            hoverBackground: Color,
            pressBackground: Color,
            metrics: IntUiIconButtonMetrics,
        ) = IntUiIconButtonStyle(
            IntUiIconButtonColors(
                background = Color.Transparent,
                backgroundDisabled = Color.Transparent,
                backgroundFocused = Color.Transparent,
                backgroundPressed = hoverBackground,
                backgroundHovered = pressBackground,
                border = Color.Transparent,
                borderDisabled = Color.Transparent,
                borderFocused = Color.Transparent,
                borderPressed = Color.Transparent,
                borderHovered = Color.Transparent,
            ),
            metrics,
        )

        @Composable
        fun light(
            colors: IntUiTitleBarColors = IntUiTitleBarColors.light(),
            metrics: IntUiTitleBarMetrics = IntUiTitleBarMetrics(),
            icons: IntUiTitleBarIcons = intUiTitleBarIcons(),
        ): IntUiTitleBarStyle = IntUiTitleBarStyle(
            colors = colors,
            metrics = metrics,
            icons = icons,
            dropdownStyle = titleBarDropdownStyle(
                colors.content,
                colors.dropdownHoverBackground,
                colors.dropdownPressBackground,
                IntUiMenuStyle.light(),
            ),
            iconButtonStyle = titleBarIconButtonStyle(
                colors.iconButtonHoverBackground,
                colors.iconButtonPressBackground,
                IntUiIconButtonMetrics(borderWidth = 0.dp),
            ),
            paneButtonStyle = titleBarIconButtonStyle(
                colors.titlePaneButtonHoverBackground,
                colors.titlePaneButtonPressBackground,
                IntUiIconButtonMetrics(CornerSize(0.dp), borderWidth = 0.dp),
            ),
            paneCloseButtonStyle = titleBarIconButtonStyle(
                colors.titlePaneCloseButtonHoverBackground,
                colors.titlePaneCloseButtonPressBackground,
                IntUiIconButtonMetrics(CornerSize(0.dp), borderWidth = 0.dp),
            ),
        )

        @Composable
        fun lightWithLightHeader(
            colors: IntUiTitleBarColors = IntUiTitleBarColors.lightWithLightHeader(),
            metrics: IntUiTitleBarMetrics = IntUiTitleBarMetrics(),
            icons: IntUiTitleBarIcons = intUiTitleBarIcons(),
        ): IntUiTitleBarStyle = IntUiTitleBarStyle(
            colors = colors,
            metrics = metrics,
            icons = icons,
            dropdownStyle = titleBarDropdownStyle(
                colors.content,
                colors.dropdownHoverBackground,
                colors.dropdownPressBackground,
                IntUiMenuStyle.light(),
            ),
            iconButtonStyle = titleBarIconButtonStyle(
                colors.iconButtonHoverBackground,
                colors.iconButtonPressBackground,
                IntUiIconButtonMetrics(borderWidth = 0.dp),
            ),
            paneButtonStyle = titleBarIconButtonStyle(
                colors.titlePaneButtonHoverBackground,
                colors.titlePaneButtonPressBackground,
                IntUiIconButtonMetrics(CornerSize(0.dp), borderWidth = 0.dp),
            ),
            paneCloseButtonStyle = titleBarIconButtonStyle(
                colors.titlePaneCloseButtonHoverBackground,
                colors.titlePaneCloseButtonPressBackground,
                IntUiIconButtonMetrics(CornerSize(0.dp), borderWidth = 0.dp),
            ),
        )

        @Composable
        fun dark(
            colors: IntUiTitleBarColors = IntUiTitleBarColors.dark(),
            metrics: IntUiTitleBarMetrics = IntUiTitleBarMetrics(),
            icons: IntUiTitleBarIcons = intUiTitleBarIcons(),
        ): IntUiTitleBarStyle = IntUiTitleBarStyle(
            colors = colors,
            metrics = metrics,
            icons = icons,
            dropdownStyle = titleBarDropdownStyle(
                colors.content,
                colors.dropdownHoverBackground,
                colors.dropdownPressBackground,
                IntUiMenuStyle.dark(),
            ),
            iconButtonStyle = titleBarIconButtonStyle(
                colors.iconButtonHoverBackground,
                colors.iconButtonPressBackground,
                IntUiIconButtonMetrics(borderWidth = 0.dp),
            ),
            paneButtonStyle = titleBarIconButtonStyle(
                colors.titlePaneButtonHoverBackground,
                colors.titlePaneButtonPressBackground,
                IntUiIconButtonMetrics(CornerSize(0.dp), borderWidth = 0.dp),
            ),
            paneCloseButtonStyle = titleBarIconButtonStyle(
                colors.titlePaneCloseButtonHoverBackground,
                colors.titlePaneCloseButtonPressBackground,
                IntUiIconButtonMetrics(CornerSize(0.dp), borderWidth = 0.dp),
            ),
        )
    }
}

@Stable
@Immutable
class IntUiTitleBarColors(
    override val background: Color,
    override val inactiveBackground: Color,
    override val content: Color,
    override val border: Color,
    override val fullscreenControlButtonsBackground: Color,
    override val titlePaneButtonHoverBackground: Color,
    override val titlePaneButtonPressBackground: Color,
    override val titlePaneCloseButtonHoverBackground: Color,
    override val titlePaneCloseButtonPressBackground: Color,
    override val iconButtonHoverBackground: Color,
    override val iconButtonPressBackground: Color,
    override val dropdownHoverBackground: Color,
    override val dropdownPressBackground: Color,
) : TitleBarColors {

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + inactiveBackground.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + border.hashCode()
        result = 31 * result + fullscreenControlButtonsBackground.hashCode()
        result = 31 * result + titlePaneButtonHoverBackground.hashCode()
        result = 31 * result + titlePaneButtonPressBackground.hashCode()
        result = 31 * result + titlePaneCloseButtonHoverBackground.hashCode()
        result = 31 * result + titlePaneCloseButtonPressBackground.hashCode()
        result = 31 * result + iconButtonHoverBackground.hashCode()
        result = 31 * result + iconButtonPressBackground.hashCode()
        result = 31 * result + dropdownHoverBackground.hashCode()
        result = 31 * result + dropdownPressBackground.hashCode()
        return result
    }

    override fun toString(): String {
        return "IntUiTitleBarColors(background=$background, " +
            "inactiveBackground=$inactiveBackground, " +
            "content=$content, " +
            "border=$border, " +
            "fullscreenControlButtonsBackground=$fullscreenControlButtonsBackground, " +
            "titlePaneButtonHoverBackground=$titlePaneButtonHoverBackground, " +
            "titlePaneButtonPressBackground=$titlePaneButtonPressBackground, " +
            "titlePaneCloseButtonHoverBackground=$titlePaneCloseButtonHoverBackground, " +
            "titlePaneCloseButtonPressBackground=$titlePaneCloseButtonPressBackground, " +
            "iconButtonHoverBackground=$iconButtonHoverBackground, " +
            "iconButtonPressBackground=$iconButtonPressBackground, " +
            "dropdownHoverBackground=$dropdownHoverBackground, " +
            "dropdownPressBackground=$dropdownPressBackground)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntUiTitleBarColors) return false

        if (background != other.background) return false
        if (inactiveBackground != other.inactiveBackground) return false
        if (content != other.content) return false
        if (border != other.border) return false
        if (fullscreenControlButtonsBackground != other.fullscreenControlButtonsBackground) return false
        if (titlePaneButtonHoverBackground != other.titlePaneButtonHoverBackground) return false
        if (titlePaneButtonPressBackground != other.titlePaneButtonPressBackground) return false
        if (titlePaneCloseButtonHoverBackground != other.titlePaneCloseButtonHoverBackground) return false
        if (titlePaneCloseButtonPressBackground != other.titlePaneCloseButtonPressBackground) return false
        if (iconButtonHoverBackground != other.iconButtonHoverBackground) return false
        if (iconButtonPressBackground != other.iconButtonPressBackground) return false
        if (dropdownHoverBackground != other.dropdownHoverBackground) return false
        if (dropdownPressBackground != other.dropdownPressBackground) return false

        return true
    }

    companion object {

        @Composable
        fun light(
            backgroundColor: Color = IntUiLightTheme.colors.grey(2),
            inactiveBackground: Color = IntUiLightTheme.colors.grey(3),
            contentColor: Color = IntUiLightTheme.colors.grey(12),
            borderColor: Color = IntUiLightTheme.colors.grey(4),
            fullscreenControlButtonsBackground: Color = Color(0xFF7A7B80),
            // Color hex from
            // com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations.titlePaneButtonHoverBackground
            titlePaneButtonHoverBackground: Color = Color(0x1AFFFFFF),
            // Same as
            // com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations.titlePaneButtonPressBackground
            titlePaneButtonPressBackground: Color = titlePaneButtonHoverBackground,
            // Color hex from
            // com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons.closeStyleBuilder
            titlePaneCloseButtonHoverBackground: Color = Color(0xFFE81123),
            titlePaneCloseButtonPressBackground: Color = Color(0xFFF1707A),

            iconButtonHoverBackground: Color = IntUiLightTheme.colors.grey(3),
            iconButtonPressBackground: Color = IntUiLightTheme.colors.grey(3),

            // There are two fields in theme.json: transparentHoverBackground and hoverBackground,
            // but in com.intellij.ide.ui.laf.darcula.ui.ToolbarComboWidgetUI#paintBackground,
            // transparentHoverBackground is used first, which is guessed to be due to the gradient background
            // caused by the project color of the titlebar, which makes the pure color background look strange
            // in the area. In order to simplify the use in Jewel, here directly use transparentHoverBackground
            // as hoverBackground.
            dropdownHoverBackground: Color = Color(0x1AFFFFFF),
            dropdownPressBackground: Color = dropdownHoverBackground,
        ) = IntUiTitleBarColors(
            background = backgroundColor,
            inactiveBackground = inactiveBackground,
            content = contentColor,
            border = borderColor,
            fullscreenControlButtonsBackground = fullscreenControlButtonsBackground,
            titlePaneButtonHoverBackground = titlePaneButtonHoverBackground,
            titlePaneButtonPressBackground = titlePaneButtonPressBackground,
            titlePaneCloseButtonHoverBackground = titlePaneCloseButtonHoverBackground,
            titlePaneCloseButtonPressBackground = titlePaneCloseButtonPressBackground,
            iconButtonHoverBackground = iconButtonHoverBackground,
            iconButtonPressBackground = iconButtonPressBackground,
            dropdownHoverBackground = dropdownHoverBackground,
            dropdownPressBackground = dropdownPressBackground,
        )

        @Composable
        fun lightWithLightHeader(
            backgroundColor: Color = IntUiLightTheme.colors.grey(13),
            inactiveBackground: Color = IntUiLightTheme.colors.grey(12),
            fullscreenControlButtonsBackground: Color = Color(0xFF7A7B80),
            contentColor: Color = IntUiLightTheme.colors.grey(1),
            borderColor: Color = IntUiLightTheme.colors.grey(11),
            titlePaneButtonHoverBackground: Color = Color(0x1A000000),
            titlePaneButtonPressBackground: Color = titlePaneButtonHoverBackground,
            titlePaneCloseButtonHoverBackground: Color = Color(0xFFE81123),
            titlePaneCloseButtonPressBackground: Color = Color(0xFFF1707A),
            iconButtonHoverBackground: Color = IntUiLightTheme.colors.grey(12),
            iconButtonPressBackground: Color = IntUiLightTheme.colors.grey(11),
            dropdownHoverBackground: Color = Color(0x0D000000),
            dropdownPressBackground: Color = dropdownHoverBackground,
        ) = IntUiTitleBarColors(
            background = backgroundColor,
            inactiveBackground = inactiveBackground,
            content = contentColor,
            border = borderColor,
            fullscreenControlButtonsBackground = fullscreenControlButtonsBackground,
            titlePaneButtonHoverBackground = titlePaneButtonHoverBackground,
            titlePaneButtonPressBackground = titlePaneButtonPressBackground,
            titlePaneCloseButtonHoverBackground = titlePaneCloseButtonHoverBackground,
            titlePaneCloseButtonPressBackground = titlePaneCloseButtonPressBackground,
            iconButtonHoverBackground = iconButtonHoverBackground,
            iconButtonPressBackground = iconButtonPressBackground,
            dropdownHoverBackground = dropdownHoverBackground,
            dropdownPressBackground = dropdownPressBackground,
        )

        @Composable
        fun dark(
            backgroundColor: Color = IntUiDarkTheme.colors.grey(2),
            inactiveBackground: Color = IntUiDarkTheme.colors.grey(3),
            fullscreenControlButtonsBackground: Color = Color(0xFF575A5C),
            contentColor: Color = IntUiDarkTheme.colors.grey(12),
            borderColor: Color = IntUiDarkTheme.colors.grey(4),
            titlePaneButtonHoverBackground: Color = Color(0x1AFFFFFF),
            titlePaneButtonPressBackground: Color = titlePaneButtonHoverBackground,
            titlePaneCloseButtonHoverBackground: Color = Color(0xFFE81123),
            titlePaneCloseButtonPressBackground: Color = Color(0xFFF1707A),
            iconButtonHoverBackground: Color = IntUiLightTheme.colors.grey(3),
            iconButtonPressBackground: Color = IntUiLightTheme.colors.grey(3),
            dropdownHoverBackground: Color = Color(0x1AFFFFFF),
            dropdownPressBackground: Color = dropdownHoverBackground,
        ) = IntUiTitleBarColors(
            background = backgroundColor,
            inactiveBackground = inactiveBackground,
            content = contentColor,
            border = borderColor,
            fullscreenControlButtonsBackground = fullscreenControlButtonsBackground,
            titlePaneButtonHoverBackground = titlePaneButtonHoverBackground,
            titlePaneButtonPressBackground = titlePaneButtonPressBackground,
            titlePaneCloseButtonHoverBackground = titlePaneCloseButtonHoverBackground,
            titlePaneCloseButtonPressBackground = titlePaneCloseButtonPressBackground,
            iconButtonHoverBackground = iconButtonHoverBackground,
            iconButtonPressBackground = iconButtonPressBackground,
            dropdownHoverBackground = dropdownHoverBackground,
            dropdownPressBackground = dropdownPressBackground,
        )
    }
}

@Stable
@Immutable
class IntUiTitleBarMetrics(
    override val height: Dp = 40.dp,
    override val gradientStartX: Dp = (-100).dp,
    override val gradientEndX: Dp = 400.dp,
    override val titlePaneButtonSize: DpSize = DpSize(40.dp, 40.dp),
) : TitleBarMetrics {

    override fun hashCode(): Int {
        var result = height.hashCode()
        result = 31 * result + gradientStartX.hashCode()
        result = 31 * result + gradientEndX.hashCode()
        result = 31 * result + titlePaneButtonSize.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntUiTitleBarMetrics) return false

        if (height != other.height) return false
        if (gradientStartX != other.gradientStartX) return false
        if (gradientEndX != other.gradientEndX) return false
        if (titlePaneButtonSize != other.titlePaneButtonSize) return false

        return true
    }

    override fun toString(): String = "IntUiTitleBarMetrics(height=$height, " +
        "gradientStartX=$gradientStartX, " +
        "gradientEndX=$gradientEndX, " +
        "titlePaneButtonSize=$titlePaneButtonSize)"
}

class IntUiTitleBarIcons(
    override val minimizeButton: PainterProvider,
    override val maximizeButton: PainterProvider,
    override val restoreButton: PainterProvider,
    override val closeButton: PainterProvider,
) : TitleBarIcons {

    override fun hashCode(): Int {
        var result = minimizeButton.hashCode()
        result = 31 * result + maximizeButton.hashCode()
        result = 31 * result + restoreButton.hashCode()
        result = 31 * result + closeButton.hashCode()
        return result
    }

    override fun toString(): String = "IntUiTitleBarIcons(minimizeButton=$minimizeButton, " +
        "maximizeButton=$maximizeButton, " +
        "restoreButton=$restoreButton, " +
        "closeButton=$closeButton)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntUiTitleBarIcons) return false

        if (minimizeButton != other.minimizeButton) return false
        if (maximizeButton != other.maximizeButton) return false
        if (restoreButton != other.restoreButton) return false
        if (closeButton != other.closeButton) return false

        return true
    }

    companion object {

        @Composable
        fun minimize(
            basePath: String = "icons/intui/window/minimize.svg",
        ): PainterProvider = decoratedWindowPainterProvider(basePath)

        @Composable
        fun maximize(
            basePath: String = "icons/intui/window/maximize.svg",
        ): PainterProvider = decoratedWindowPainterProvider(basePath)

        @Composable
        fun restore(
            basePath: String = "icons/intui/window/restore.svg",
        ): PainterProvider = decoratedWindowPainterProvider(basePath)

        @Composable
        fun close(
            basePath: String = "icons/intui/window/close.svg",
        ): PainterProvider = decoratedWindowPainterProvider(basePath)
    }
}

@Composable
fun intUiTitleBarIcons(
    minimize: PainterProvider = IntUiTitleBarIcons.minimize(),
    maximize: PainterProvider = IntUiTitleBarIcons.maximize(),
    restore: PainterProvider = IntUiTitleBarIcons.restore(),
    close: PainterProvider = IntUiTitleBarIcons.close(),
) = IntUiTitleBarIcons(minimize, maximize, restore, close)
