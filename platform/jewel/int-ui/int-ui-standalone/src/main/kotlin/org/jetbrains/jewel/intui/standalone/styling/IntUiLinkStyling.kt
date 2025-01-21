package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.LinkColors
import org.jetbrains.jewel.ui.component.styling.LinkIcons
import org.jetbrains.jewel.ui.component.styling.LinkMetrics
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.LinkUnderlineBehavior
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

public fun LinkStyle.Companion.light(
    colors: LinkColors = LinkColors.light(),
    metrics: LinkMetrics = LinkMetrics.defaults(),
    icons: LinkIcons = LinkIcons.defaults(),
    underlineBehavior: LinkUnderlineBehavior = LinkUnderlineBehavior.ShowOnHover,
): LinkStyle = LinkStyle(colors, metrics, icons, underlineBehavior)

public fun LinkStyle.Companion.dark(
    colors: LinkColors = LinkColors.dark(),
    metrics: LinkMetrics = LinkMetrics.defaults(),
    icons: LinkIcons = LinkIcons.defaults(),
    underlineBehavior: LinkUnderlineBehavior = LinkUnderlineBehavior.ShowOnHover,
): LinkStyle = LinkStyle(colors, metrics, icons, underlineBehavior)

public fun LinkColors.Companion.light(
    content: Color = IntUiLightTheme.colors.blue(2),
    contentDisabled: Color = IntUiLightTheme.colors.gray(8),
    contentFocused: Color = content,
    contentPressed: Color = content,
    contentHovered: Color = content,
    contentVisited: Color = content,
): LinkColors =
    LinkColors(
        content = content,
        contentDisabled = contentDisabled,
        contentFocused = contentFocused,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
        contentVisited = contentVisited,
    )

public fun LinkColors.Companion.dark(
    content: Color = IntUiDarkTheme.colors.blue(9),
    contentDisabled: Color = IntUiDarkTheme.colors.gray(7),
    contentFocused: Color = content,
    contentPressed: Color = content,
    contentHovered: Color = content,
    contentVisited: Color = content,
): LinkColors =
    LinkColors(
        content = content,
        contentDisabled = contentDisabled,
        contentFocused = contentFocused,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
        contentVisited = contentVisited,
    )

public fun LinkMetrics.Companion.defaults(
    focusHaloCornerSize: CornerSize = CornerSize(2.dp),
    textIconGap: Dp = 0.dp,
    iconSize: DpSize = DpSize(16.dp, 16.dp),
): LinkMetrics = LinkMetrics(focusHaloCornerSize, textIconGap, iconSize)

public fun LinkIcons.Companion.defaults(
    dropdownChevron: IconKey = AllIconsKeys.General.ChevronDown,
    externalLink: IconKey = AllIconsKeys.Ide.External_link_arrow,
): LinkIcons = LinkIcons(dropdownChevron, externalLink)
