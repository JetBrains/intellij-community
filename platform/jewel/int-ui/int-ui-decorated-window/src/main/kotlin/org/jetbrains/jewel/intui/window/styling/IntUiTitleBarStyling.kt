package org.jetbrains.jewel.intui.window.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.GenerateDataFunctions
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.styling.Undecorated
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.intui.standalone.styling.defaults
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.intui.window.decoratedWindowPainterProvider
import org.jetbrains.jewel.painter.PainterProvider
import org.jetbrains.jewel.styling.DropdownColors
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.IconButtonColors
import org.jetbrains.jewel.styling.IconButtonMetrics
import org.jetbrains.jewel.styling.IconButtonStyle
import org.jetbrains.jewel.styling.MenuStyle
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarIcons
import org.jetbrains.jewel.window.styling.TitleBarMetrics
import org.jetbrains.jewel.window.styling.TitleBarStyle

@Immutable
@GenerateDataFunctions
class IntUiTitleBarStyle(
    override val colors: IntUiTitleBarColors,
    override val metrics: IntUiTitleBarMetrics,
    override val icons: TitleBarIcons,
    override val dropdownStyle: DropdownStyle,
    override val iconButtonStyle: IconButtonStyle,
    override val paneButtonStyle: IconButtonStyle,
    override val paneCloseButtonStyle: IconButtonStyle,
) : TitleBarStyle {

    companion object {

        @Composable
        private fun titleBarIconButtonStyle(
            hoveredBackground: Color,
            pressedBackground: Color,
            metrics: IconButtonMetrics,
        ) = IconButtonStyle(
            IconButtonColors(
                background = Color.Transparent,
                backgroundDisabled = Color.Transparent,
                backgroundFocused = Color.Transparent,
                backgroundPressed = hoveredBackground,
                backgroundHovered = pressedBackground,
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
            dropdownStyle = DropdownStyle.Undecorated.light(
                colors = DropdownColors.Undecorated.light(
                    content = colors.content,
                    contentFocused = colors.content,
                    contentHovered = colors.content,
                    contentPressed = colors.content,
                    contentDisabled = colors.content,
                    backgroundHovered = colors.dropdownHoveredBackground,
                    backgroundPressed = colors.dropdownPressedBackground,
                ),
                menuStyle = MenuStyle.light(),
            ),
            iconButtonStyle = titleBarIconButtonStyle(
                colors.iconButtonHoveredBackground,
                colors.iconButtonPressedBackground,
                IconButtonMetrics.defaults(borderWidth = 0.dp),
            ),
            paneButtonStyle = titleBarIconButtonStyle(
                colors.titlePaneButtonHoveredBackground,
                colors.titlePaneButtonPressedBackground,
                IconButtonMetrics.defaults(cornerSize = CornerSize(0.dp), borderWidth = 0.dp),
            ),
            paneCloseButtonStyle = titleBarIconButtonStyle(
                colors.titlePaneCloseButtonHoveredBackground,
                colors.titlePaneCloseButtonPressedBackground,
                IconButtonMetrics.defaults(cornerSize = CornerSize(0.dp), borderWidth = 0.dp),
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
            dropdownStyle = DropdownStyle.Undecorated.light(
                colors = DropdownColors.Undecorated.light(
                    content = colors.content,
                    contentFocused = colors.content,
                    contentHovered = colors.content,
                    contentPressed = colors.content,
                    contentDisabled = colors.content,
                    backgroundHovered = colors.dropdownHoveredBackground,
                    backgroundPressed = colors.dropdownPressedBackground,
                ),
                menuStyle = MenuStyle.light(),
            ),
            iconButtonStyle = titleBarIconButtonStyle(
                colors.iconButtonHoveredBackground,
                colors.iconButtonPressedBackground,
                IconButtonMetrics.defaults(borderWidth = 0.dp),
            ),
            paneButtonStyle = titleBarIconButtonStyle(
                colors.titlePaneButtonHoveredBackground,
                colors.titlePaneButtonPressedBackground,
                IconButtonMetrics.defaults(cornerSize = CornerSize(0.dp), borderWidth = 0.dp),
            ),
            paneCloseButtonStyle = titleBarIconButtonStyle(
                colors.titlePaneCloseButtonHoveredBackground,
                colors.titlePaneCloseButtonPressedBackground,
                IconButtonMetrics.defaults(cornerSize = CornerSize(0.dp), borderWidth = 0.dp),
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
            dropdownStyle = DropdownStyle.Undecorated.dark(
                colors = DropdownColors.Undecorated.dark(
                    content = colors.content,
                    contentFocused = colors.content,
                    contentHovered = colors.content,
                    contentPressed = colors.content,
                    contentDisabled = colors.content,
                    backgroundHovered = colors.dropdownHoveredBackground,
                    backgroundPressed = colors.dropdownPressedBackground,
                ),
                menuStyle = MenuStyle.dark(),
            ),
            iconButtonStyle = titleBarIconButtonStyle(
                colors.iconButtonHoveredBackground,
                colors.iconButtonPressedBackground,
                IconButtonMetrics.defaults(borderWidth = 0.dp),
            ),
            paneButtonStyle = titleBarIconButtonStyle(
                colors.titlePaneButtonHoveredBackground,
                colors.titlePaneButtonPressedBackground,
                IconButtonMetrics.defaults(cornerSize = CornerSize(0.dp), borderWidth = 0.dp),
            ),
            paneCloseButtonStyle = titleBarIconButtonStyle(
                colors.titlePaneCloseButtonHoveredBackground,
                colors.titlePaneCloseButtonPressedBackground,
                IconButtonMetrics.defaults(cornerSize = CornerSize(0.dp), borderWidth = 0.dp),
            ),
        )
    }
}

@Immutable
@GenerateDataFunctions
class IntUiTitleBarColors(
    override val background: Color,
    override val inactiveBackground: Color,
    override val content: Color,
    override val border: Color,
    override val fullscreenControlButtonsBackground: Color,
    override val titlePaneButtonHoveredBackground: Color,
    override val titlePaneButtonPressedBackground: Color,
    override val titlePaneCloseButtonHoveredBackground: Color,
    override val titlePaneCloseButtonPressedBackground: Color,
    override val iconButtonHoveredBackground: Color,
    override val iconButtonPressedBackground: Color,
    override val dropdownHoveredBackground: Color,
    override val dropdownPressedBackground: Color,
) : TitleBarColors {

    companion object {

        @Composable
        fun light(
            backgroundColor: Color = IntUiLightTheme.colors.grey(2),
            inactiveBackground: Color = IntUiLightTheme.colors.grey(3),
            contentColor: Color = IntUiLightTheme.colors.grey(12),
            borderColor: Color = IntUiLightTheme.colors.grey(4),
            fullscreenControlButtonsBackground: Color = Color(0xFF7A7B80),
            // Color hex from
            // com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations.titlePaneButtonHoveredBackground
            titlePaneButtonHoveredBackground: Color = Color(0x1AFFFFFF),
            // Same as
            // com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations.titlePaneButtonPressedBackground
            titlePaneButtonPressedBackground: Color = titlePaneButtonHoveredBackground,
            // Color hex from
            // com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons.closeStyleBuilder
            titlePaneCloseButtonHoveredBackground: Color = Color(0xFFE81123),
            titlePaneCloseButtonPressedBackground: Color = Color(0xFFF1707A),

            iconButtonHoveredBackground: Color = IntUiLightTheme.colors.grey(3),
            iconButtonPressedBackground: Color = IntUiLightTheme.colors.grey(3),

            // There are two fields in theme.json: transparentHoveredBackground and hoveredBackground,
            // but in com.intellij.ide.ui.laf.darcula.ui.ToolbarComboWidgetUI#paintBackground,
            // transparentHoveredBackground is used first, which is guessed to be due to the gradient background
            // caused by the project color of the titlebar, which makes the pure color background look strange
            // in the area. In order to simplify the use in Jewel, here directly use transparentHoveredBackground
            // as hoveredBackground.
            dropdownHoveredBackground: Color = Color(0x1AFFFFFF),
            dropdownPressedBackground: Color = dropdownHoveredBackground,
        ) = IntUiTitleBarColors(
            background = backgroundColor,
            inactiveBackground = inactiveBackground,
            content = contentColor,
            border = borderColor,
            fullscreenControlButtonsBackground = fullscreenControlButtonsBackground,
            titlePaneButtonHoveredBackground = titlePaneButtonHoveredBackground,
            titlePaneButtonPressedBackground = titlePaneButtonPressedBackground,
            titlePaneCloseButtonHoveredBackground = titlePaneCloseButtonHoveredBackground,
            titlePaneCloseButtonPressedBackground = titlePaneCloseButtonPressedBackground,
            iconButtonHoveredBackground = iconButtonHoveredBackground,
            iconButtonPressedBackground = iconButtonPressedBackground,
            dropdownHoveredBackground = dropdownHoveredBackground,
            dropdownPressedBackground = dropdownPressedBackground,
        )

        @Composable
        fun lightWithLightHeader(
            backgroundColor: Color = IntUiLightTheme.colors.grey(13),
            inactiveBackground: Color = IntUiLightTheme.colors.grey(12),
            fullscreenControlButtonsBackground: Color = Color(0xFF7A7B80),
            contentColor: Color = IntUiLightTheme.colors.grey(1),
            borderColor: Color = IntUiLightTheme.colors.grey(11),
            titlePaneButtonHoveredBackground: Color = Color(0x1A000000),
            titlePaneButtonPressedBackground: Color = titlePaneButtonHoveredBackground,
            titlePaneCloseButtonHoveredBackground: Color = Color(0xFFE81123),
            titlePaneCloseButtonPressedBackground: Color = Color(0xFFF1707A),
            iconButtonHoveredBackground: Color = IntUiLightTheme.colors.grey(12),
            iconButtonPressedBackground: Color = IntUiLightTheme.colors.grey(11),
            dropdownHoveredBackground: Color = Color(0x0D000000),
            dropdownPressedBackground: Color = dropdownHoveredBackground,
        ) = IntUiTitleBarColors(
            background = backgroundColor,
            inactiveBackground = inactiveBackground,
            content = contentColor,
            border = borderColor,
            fullscreenControlButtonsBackground = fullscreenControlButtonsBackground,
            titlePaneButtonHoveredBackground = titlePaneButtonHoveredBackground,
            titlePaneButtonPressedBackground = titlePaneButtonPressedBackground,
            titlePaneCloseButtonHoveredBackground = titlePaneCloseButtonHoveredBackground,
            titlePaneCloseButtonPressedBackground = titlePaneCloseButtonPressedBackground,
            iconButtonHoveredBackground = iconButtonHoveredBackground,
            iconButtonPressedBackground = iconButtonPressedBackground,
            dropdownHoveredBackground = dropdownHoveredBackground,
            dropdownPressedBackground = dropdownPressedBackground,
        )

        @Composable
        fun dark(
            backgroundColor: Color = IntUiDarkTheme.colors.grey(2),
            inactiveBackground: Color = IntUiDarkTheme.colors.grey(3),
            fullscreenControlButtonsBackground: Color = Color(0xFF575A5C),
            contentColor: Color = IntUiDarkTheme.colors.grey(12),
            borderColor: Color = IntUiDarkTheme.colors.grey(4),
            titlePaneButtonHoveredBackground: Color = Color(0x1AFFFFFF),
            titlePaneButtonPressedBackground: Color = titlePaneButtonHoveredBackground,
            titlePaneCloseButtonHoveredBackground: Color = Color(0xFFE81123),
            titlePaneCloseButtonPressedBackground: Color = Color(0xFFF1707A),
            iconButtonHoveredBackground: Color = IntUiLightTheme.colors.grey(3),
            iconButtonPressedBackground: Color = IntUiLightTheme.colors.grey(3),
            dropdownHoveredBackground: Color = Color(0x1AFFFFFF),
            dropdownPressedBackground: Color = dropdownHoveredBackground,
        ) = IntUiTitleBarColors(
            background = backgroundColor,
            inactiveBackground = inactiveBackground,
            content = contentColor,
            border = borderColor,
            fullscreenControlButtonsBackground = fullscreenControlButtonsBackground,
            titlePaneButtonHoveredBackground = titlePaneButtonHoveredBackground,
            titlePaneButtonPressedBackground = titlePaneButtonPressedBackground,
            titlePaneCloseButtonHoveredBackground = titlePaneCloseButtonHoveredBackground,
            titlePaneCloseButtonPressedBackground = titlePaneCloseButtonPressedBackground,
            iconButtonHoveredBackground = iconButtonHoveredBackground,
            iconButtonPressedBackground = iconButtonPressedBackground,
            dropdownHoveredBackground = dropdownHoveredBackground,
            dropdownPressedBackground = dropdownPressedBackground,
        )
    }
}

@Immutable
@GenerateDataFunctions
class IntUiTitleBarMetrics(
    override val height: Dp = 40.dp,
    override val gradientStartX: Dp = (-100).dp,
    override val gradientEndX: Dp = 400.dp,
    override val titlePaneButtonSize: DpSize = DpSize(40.dp, 40.dp),
) : TitleBarMetrics

@Immutable
@GenerateDataFunctions
class IntUiTitleBarIcons(
    override val minimizeButton: PainterProvider,
    override val maximizeButton: PainterProvider,
    override val restoreButton: PainterProvider,
    override val closeButton: PainterProvider,
) : TitleBarIcons {

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
