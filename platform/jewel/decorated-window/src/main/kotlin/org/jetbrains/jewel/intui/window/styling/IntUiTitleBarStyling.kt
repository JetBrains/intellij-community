package org.jetbrains.jewel.intui.window.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.styling.Undecorated
import org.jetbrains.jewel.intui.standalone.styling.defaults
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.intui.standalone.styling.undecorated
import org.jetbrains.jewel.intui.window.DecoratedWindowIconKeys
import org.jetbrains.jewel.ui.component.styling.DropdownColors
import org.jetbrains.jewel.ui.component.styling.DropdownMetrics
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.window.styling.TitleBarColors
import org.jetbrains.jewel.window.styling.TitleBarIcons
import org.jetbrains.jewel.window.styling.TitleBarMetrics
import org.jetbrains.jewel.window.styling.TitleBarStyle

/**
 * Creates an Int UI light [TitleBarStyle] with the standard IntelliJ dark-header appearance.
 *
 * @param colors The [TitleBarColors] to use. Defaults to [TitleBarColors.light].
 * @param metrics The [TitleBarMetrics] to use. Defaults to [TitleBarMetrics.defaults].
 * @param icons The [TitleBarIcons] to use. Defaults to [TitleBarIcons.defaults].
 */
@Composable
public fun TitleBarStyle.Companion.light(
    colors: TitleBarColors = TitleBarColors.light(),
    metrics: TitleBarMetrics = TitleBarMetrics.defaults(),
    icons: TitleBarIcons = TitleBarIcons.defaults(),
): TitleBarStyle =
    TitleBarStyle(
        colors = colors,
        metrics = metrics,
        icons = icons,
        dropdownStyle =
            DropdownStyle.Undecorated.dark(
                colors =
                    DropdownColors.Undecorated.dark(
                        content = colors.content,
                        contentFocused = colors.content,
                        contentHovered = colors.content,
                        contentPressed = colors.content,
                        contentDisabled = colors.content,
                        backgroundHovered = colors.dropdownHoveredBackground,
                        backgroundPressed = colors.dropdownPressedBackground,
                    ),
                metrics =
                    DropdownMetrics.undecorated(
                        arrowMinSize = DpSize(20.dp, 24.dp),
                        minSize = DpSize(60.dp, 30.dp),
                        cornerSize = CornerSize(6.dp),
                        contentPadding = PaddingValues(start = 10.dp, end = 0.dp, top = 3.dp, bottom = 3.dp),
                    ),
                menuStyle = MenuStyle.light(),
            ),
        iconButtonStyle =
            titleBarIconButtonStyle(
                colors.iconButtonHoveredBackground,
                colors.iconButtonPressedBackground,
                IconButtonMetrics.defaults(borderWidth = 0.dp),
            ),
        paneButtonStyle =
            titleBarIconButtonStyle(
                colors.titlePaneButtonHoveredBackground,
                colors.titlePaneButtonPressedBackground,
                IconButtonMetrics.defaults(cornerSize = CornerSize(0.dp), borderWidth = 0.dp),
            ),
        paneCloseButtonStyle =
            titleBarIconButtonStyle(
                colors.titlePaneCloseButtonHoveredBackground,
                colors.titlePaneCloseButtonPressedBackground,
                IconButtonMetrics.defaults(cornerSize = CornerSize(0.dp), borderWidth = 0.dp),
            ),
    )

/**
 * Creates an Int UI [TitleBarStyle] with a light-colored header, matching the "New UI" appearance.
 *
 * @param colors The [TitleBarColors] to use. Defaults to [TitleBarColors.lightWithLightHeader].
 * @param metrics The [TitleBarMetrics] to use. Defaults to [TitleBarMetrics.defaults].
 * @param icons The [TitleBarIcons] to use. Defaults to [TitleBarIcons.defaults].
 */
@Composable
public fun TitleBarStyle.Companion.lightWithLightHeader(
    colors: TitleBarColors = TitleBarColors.lightWithLightHeader(),
    metrics: TitleBarMetrics = TitleBarMetrics.defaults(),
    icons: TitleBarIcons = TitleBarIcons.defaults(),
): TitleBarStyle =
    TitleBarStyle(
        colors = colors,
        metrics = metrics,
        icons = icons,
        dropdownStyle =
            DropdownStyle.Undecorated.light(
                colors =
                    DropdownColors.Undecorated.light(
                        content = colors.content,
                        contentFocused = colors.content,
                        contentHovered = colors.content,
                        contentPressed = colors.content,
                        contentDisabled = colors.content,
                        backgroundHovered = colors.dropdownHoveredBackground,
                        backgroundPressed = colors.dropdownPressedBackground,
                    ),
                metrics =
                    DropdownMetrics.undecorated(
                        arrowMinSize = DpSize(20.dp, 24.dp),
                        minSize = DpSize(60.dp, 30.dp),
                        cornerSize = CornerSize(6.dp),
                        contentPadding = PaddingValues(start = 10.dp, end = 0.dp, top = 3.dp, bottom = 3.dp),
                    ),
            ),
        iconButtonStyle =
            titleBarIconButtonStyle(
                colors.iconButtonHoveredBackground,
                colors.iconButtonPressedBackground,
                IconButtonMetrics.defaults(borderWidth = 0.dp),
            ),
        paneButtonStyle =
            titleBarIconButtonStyle(
                colors.titlePaneButtonHoveredBackground,
                colors.titlePaneButtonPressedBackground,
                IconButtonMetrics.defaults(cornerSize = CornerSize(0.dp), borderWidth = 0.dp),
            ),
        paneCloseButtonStyle =
            titleBarIconButtonStyle(
                colors.titlePaneCloseButtonHoveredBackground,
                colors.titlePaneCloseButtonPressedBackground,
                IconButtonMetrics.defaults(cornerSize = CornerSize(0.dp), borderWidth = 0.dp),
            ),
    )

/**
 * Creates an Int UI dark [TitleBarStyle].
 *
 * @param colors The [TitleBarColors] to use. Defaults to [TitleBarColors.dark].
 * @param metrics The [TitleBarMetrics] to use. Defaults to [TitleBarMetrics.defaults].
 * @param icons The [TitleBarIcons] to use. Defaults to [TitleBarIcons.defaults].
 */
@Composable
public fun TitleBarStyle.Companion.dark(
    colors: TitleBarColors = TitleBarColors.dark(),
    metrics: TitleBarMetrics = TitleBarMetrics.defaults(),
    icons: TitleBarIcons = TitleBarIcons.defaults(),
): TitleBarStyle =
    TitleBarStyle(
        colors = colors,
        metrics = metrics,
        icons = icons,
        dropdownStyle =
            DropdownStyle.Undecorated.dark(
                colors =
                    DropdownColors.Undecorated.dark(
                        content = colors.content,
                        contentFocused = colors.content,
                        contentHovered = colors.content,
                        contentPressed = colors.content,
                        contentDisabled = colors.content,
                        backgroundHovered = colors.dropdownHoveredBackground,
                        backgroundPressed = colors.dropdownPressedBackground,
                    ),
                metrics =
                    DropdownMetrics.undecorated(
                        arrowMinSize = DpSize(20.dp, 24.dp),
                        minSize = DpSize(60.dp, 30.dp),
                        cornerSize = CornerSize(6.dp),
                        contentPadding = PaddingValues(start = 10.dp, end = 0.dp, top = 3.dp, bottom = 3.dp),
                    ),
            ),
        iconButtonStyle =
            titleBarIconButtonStyle(
                colors.iconButtonHoveredBackground,
                colors.iconButtonPressedBackground,
                IconButtonMetrics.defaults(borderWidth = 0.dp),
            ),
        paneButtonStyle =
            titleBarIconButtonStyle(
                colors.titlePaneButtonHoveredBackground,
                colors.titlePaneButtonPressedBackground,
                IconButtonMetrics.defaults(cornerSize = CornerSize(0.dp), borderWidth = 0.dp),
            ),
        paneCloseButtonStyle =
            titleBarIconButtonStyle(
                colors.titlePaneCloseButtonHoveredBackground,
                colors.titlePaneCloseButtonPressedBackground,
                IconButtonMetrics.defaults(cornerSize = CornerSize(0.dp), borderWidth = 0.dp),
            ),
    )

private fun titleBarIconButtonStyle(hoveredBackground: Color, pressedBackground: Color, metrics: IconButtonMetrics) =
    IconButtonStyle(
        IconButtonColors(
            foregroundSelectedActivated = Color.Unspecified,
            background = Color.Unspecified,
            backgroundDisabled = Color.Unspecified,
            backgroundSelected = Color.Unspecified,
            backgroundSelectedActivated = Color.Unspecified,
            backgroundFocused = Color.Unspecified,
            backgroundPressed = hoveredBackground,
            backgroundHovered = pressedBackground,
            border = Color.Unspecified,
            borderDisabled = Color.Unspecified,
            borderSelected = Color.Unspecified,
            borderSelectedActivated = Color.Unspecified,
            borderFocused = hoveredBackground,
            borderPressed = pressedBackground,
            borderHovered = Color.Unspecified,
        ),
        metrics,
    )

/** Creates Int UI light [TitleBarColors] for the standard IntelliJ dark-header title bar. */
@Composable
public fun TitleBarColors.Companion.light(
    backgroundColor: Color = IntUiLightTheme.colors.gray(2),
    inactiveBackground: Color = IntUiLightTheme.colors.gray(3),
    contentColor: Color = IntUiLightTheme.colors.gray(12),
    borderColor: Color = IntUiLightTheme.colors.gray(4),
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
    iconButtonHoveredBackground: Color = IntUiLightTheme.colors.gray(3),
    iconButtonPressedBackground: Color = IntUiLightTheme.colors.gray(3),
    // There are two fields in theme.json: transparentHoveredBackground and hoveredBackground,
    // but in com.intellij.ide.ui.laf.darcula.ui.ToolbarComboWidgetUI#paintBackground,
    // transparentHoveredBackground is used first, which is guessed to be due to the gradient
    // background
    // caused by the project color of the titlebar, which makes the pure color background look
    // strange
    // in the area. In order to simplify the use in Jewel, here directly use
    // transparentHoveredBackground
    // as hoveredBackground.
    dropdownHoveredBackground: Color = Color(0x1AFFFFFF),
    dropdownPressedBackground: Color = dropdownHoveredBackground,
): TitleBarColors =
    TitleBarColors(
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

/** Creates Int UI [TitleBarColors] for a light-colored title bar header, as used in the New UI. */
@Composable
public fun TitleBarColors.Companion.lightWithLightHeader(
    backgroundColor: Color = IntUiLightTheme.colors.gray(13),
    inactiveBackground: Color = IntUiLightTheme.colors.gray(12),
    fullscreenControlButtonsBackground: Color = Color(0xFF7A7B80),
    contentColor: Color = IntUiLightTheme.colors.gray(1),
    borderColor: Color = IntUiLightTheme.colors.gray(11),
    titlePaneButtonHoveredBackground: Color = Color(0x1A000000),
    titlePaneButtonPressedBackground: Color = titlePaneButtonHoveredBackground,
    titlePaneCloseButtonHoveredBackground: Color = Color(0xFFE81123),
    titlePaneCloseButtonPressedBackground: Color = Color(0xFFF1707A),
    iconButtonHoveredBackground: Color = IntUiLightTheme.colors.gray(12),
    iconButtonPressedBackground: Color = IntUiLightTheme.colors.gray(11),
    dropdownHoveredBackground: Color = Color(0x0D000000),
    dropdownPressedBackground: Color = dropdownHoveredBackground,
): TitleBarColors =
    TitleBarColors(
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

/** Creates Int UI dark [TitleBarColors]. */
@Composable
public fun TitleBarColors.Companion.dark(
    backgroundColor: Color = IntUiDarkTheme.colors.gray(2),
    inactiveBackground: Color = IntUiDarkTheme.colors.gray(3),
    fullscreenControlButtonsBackground: Color = Color(0xFF575A5C),
    contentColor: Color = IntUiDarkTheme.colors.gray(12),
    borderColor: Color = IntUiDarkTheme.colors.gray(4),
    titlePaneButtonHoveredBackground: Color = Color(0x1AFFFFFF),
    titlePaneButtonPressedBackground: Color = titlePaneButtonHoveredBackground,
    titlePaneCloseButtonHoveredBackground: Color = Color(0xFFE81123),
    titlePaneCloseButtonPressedBackground: Color = Color(0xFFF1707A),
    iconButtonHoveredBackground: Color = IntUiLightTheme.colors.gray(3),
    iconButtonPressedBackground: Color = IntUiLightTheme.colors.gray(3),
    dropdownHoveredBackground: Color = Color(0x1AFFFFFF),
    dropdownPressedBackground: Color = dropdownHoveredBackground,
): TitleBarColors =
    TitleBarColors(
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

/**
 * Creates default [TitleBarMetrics] with standard Int UI values.
 *
 * @param height The title bar height. Defaults to `40.dp`.
 * @param gradientStartX The horizontal start position of the background gradient. Defaults to `-100.dp`.
 * @param gradientEndX The horizontal end position of the background gradient. Defaults to `400.dp`.
 * @param titlePaneButtonSize The size of window control buttons (minimize, maximize, close). Defaults to `40x40.dp`.
 */
public fun TitleBarMetrics.Companion.defaults(
    height: Dp = 40.dp,
    gradientStartX: Dp = (-100).dp,
    gradientEndX: Dp = 400.dp,
    titlePaneButtonSize: DpSize = DpSize(40.dp, 40.dp),
): TitleBarMetrics = TitleBarMetrics(height, gradientStartX, gradientEndX, titlePaneButtonSize)

/**
 * Creates default [TitleBarIcons] using the standard [DecoratedWindowIconKeys] for window controls.
 *
 * @param minimizeButton The icon key for the minimize button.
 * @param maximizeButton The icon key for the maximize button.
 * @param restoreButton The icon key for the restore button.
 * @param closeButton The icon key for the close button.
 */
public fun TitleBarIcons.Companion.defaults(
    minimizeButton: IconKey = DecoratedWindowIconKeys.minimize,
    maximizeButton: IconKey = DecoratedWindowIconKeys.maximize,
    restoreButton: IconKey = DecoratedWindowIconKeys.restore,
    closeButton: IconKey = DecoratedWindowIconKeys.close,
): TitleBarIcons = TitleBarIcons(minimizeButton, maximizeButton, restoreButton, closeButton)
