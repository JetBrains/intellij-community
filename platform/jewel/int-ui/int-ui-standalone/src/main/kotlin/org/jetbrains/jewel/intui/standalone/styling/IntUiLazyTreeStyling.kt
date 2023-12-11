package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.standalonePainterProvider
import org.jetbrains.jewel.ui.component.styling.LazyTreeColors
import org.jetbrains.jewel.ui.component.styling.LazyTreeIcons
import org.jetbrains.jewel.ui.component.styling.LazyTreeMetrics
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.ui.painter.PainterProvider

@Composable
public fun LazyTreeStyle.Companion.light(
    colors: LazyTreeColors = LazyTreeColors.light(),
    metrics: LazyTreeMetrics = LazyTreeMetrics.defaults(),
    icons: LazyTreeIcons = LazyTreeIcons.defaults(),
): LazyTreeStyle =
    LazyTreeStyle(colors, metrics, icons)

@Composable
public fun LazyTreeStyle.Companion.dark(
    colors: LazyTreeColors = LazyTreeColors.dark(),
    metrics: LazyTreeMetrics = LazyTreeMetrics.defaults(),
    icons: LazyTreeIcons = LazyTreeIcons.defaults(),
): LazyTreeStyle =
    LazyTreeStyle(colors, metrics, icons)

@Composable
public fun LazyTreeColors.Companion.light(
    content: Color = Color.Unspecified,
    contentFocused: Color = content,
    contentSelected: Color = content,
    contentSelectedFocused: Color = content,
    nodeBackgroundFocused: Color = Color.Unspecified,
    nodeBackgroundSelected: Color = IntUiLightTheme.colors.grey(11),
    nodeBackgroundSelectedFocused: Color = IntUiLightTheme.colors.blue(11),
): LazyTreeColors =
    LazyTreeColors(
        elementBackgroundFocused = nodeBackgroundFocused,
        elementBackgroundSelected = nodeBackgroundSelected,
        elementBackgroundSelectedFocused = nodeBackgroundSelectedFocused,
        content = content,
        contentFocused = contentFocused,
        contentSelected = contentSelected,
        contentSelectedFocused = contentSelectedFocused,
    )

@Composable
public fun LazyTreeColors.Companion.dark(
    content: Color = Color.Unspecified,
    contentFocused: Color = content,
    contentSelected: Color = content,
    contentSelectedFocused: Color = content,
    nodeBackgroundFocused: Color = Color.Unspecified,
    nodeBackgroundSelected: Color = IntUiDarkTheme.colors.grey(4),
    nodeBackgroundSelectedFocused: Color = IntUiDarkTheme.colors.blue(2),
): LazyTreeColors =
    LazyTreeColors(
        elementBackgroundFocused = nodeBackgroundFocused,
        elementBackgroundSelected = nodeBackgroundSelected,
        elementBackgroundSelectedFocused = nodeBackgroundSelectedFocused,
        content = content,
        contentFocused = contentFocused,
        contentSelected = contentSelected,
        contentSelectedFocused = contentSelectedFocused,
    )

public fun LazyTreeMetrics.Companion.defaults(
    indentSize: Dp = 7.dp + 16.dp,
    elementBackgroundCornerSize: CornerSize = CornerSize(2.dp),
    elementPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    elementContentPadding: PaddingValues = PaddingValues(4.dp),
    elementMinHeight: Dp = 24.dp,
    chevronContentGap: Dp = 2.dp,
): LazyTreeMetrics =
    LazyTreeMetrics(
        indentSize,
        elementBackgroundCornerSize,
        elementPadding,
        elementContentPadding,
        elementMinHeight,
        chevronContentGap,
    )

public fun LazyTreeIcons.Companion.defaults(
    chevronCollapsed: PainterProvider = standalonePainterProvider("expui/general/chevronRight.svg"),
    chevronExpanded: PainterProvider = standalonePainterProvider("expui/general/chevronDown.svg"),
    chevronSelectedCollapsed: PainterProvider = chevronCollapsed,
    chevronSelectedExpanded: PainterProvider = chevronExpanded,
): LazyTreeIcons =
    LazyTreeIcons(
        chevronCollapsed,
        chevronExpanded,
        chevronSelectedCollapsed,
        chevronSelectedExpanded,
    )
