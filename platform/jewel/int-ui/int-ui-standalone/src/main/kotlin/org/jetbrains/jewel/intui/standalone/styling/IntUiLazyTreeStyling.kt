package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.LazyTreeIcons
import org.jetbrains.jewel.ui.component.styling.LazyTreeMetrics
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val SimpleListItemStyle.Companion.LazyTree: IntUiDefaultSimpleListItemLazyTreeStyleFactory
    get() = IntUiDefaultSimpleListItemLazyTreeStyleFactory

public object IntUiDefaultSimpleListItemLazyTreeStyleFactory {
    @Composable
    public fun light(
        content: Color = Color.Unspecified,
        contentFocused: Color = content,
        contentSelected: Color = content,
        contentSelectedFocused: Color = content,
        nodeBackgroundFocused: Color = Color.Unspecified,
        nodeBackgroundSelected: Color = IntUiLightTheme.colors.gray(11),
        nodeBackgroundSelectedFocused: Color = IntUiLightTheme.colors.blue(11),
    ): SimpleListItemColors =
        SimpleListItemColors(
            backgroundFocused = nodeBackgroundFocused,
            backgroundSelected = nodeBackgroundSelected,
            backgroundSelectedFocused = nodeBackgroundSelectedFocused,
            content = content,
            contentFocused = contentFocused,
            contentSelected = contentSelected,
            contentSelectedFocused = contentSelectedFocused,
        )

    @Composable
    public fun dark(
        content: Color = Color.Unspecified,
        contentFocused: Color = content,
        contentSelected: Color = content,
        contentSelectedFocused: Color = content,
        nodeBackgroundFocused: Color = Color.Unspecified,
        nodeBackgroundSelected: Color = IntUiDarkTheme.colors.gray(4),
        nodeBackgroundSelectedFocused: Color = IntUiDarkTheme.colors.blue(2),
    ): SimpleListItemColors =
        SimpleListItemColors(
            backgroundFocused = nodeBackgroundFocused,
            backgroundSelected = nodeBackgroundSelected,
            backgroundSelectedFocused = nodeBackgroundSelectedFocused,
            content = content,
            contentFocused = contentFocused,
            contentSelected = contentSelected,
            contentSelectedFocused = contentSelectedFocused,
        )
}

@Composable
public fun LazyTreeStyle.Companion.light(
    colors: SimpleListItemColors = SimpleListItemStyle.LazyTree.light(),
    metrics: LazyTreeMetrics = LazyTreeMetrics.defaults(),
    icons: LazyTreeIcons = LazyTreeIcons.defaults(),
): LazyTreeStyle = LazyTreeStyle(colors, metrics, icons)

@Composable
public fun LazyTreeStyle.Companion.dark(
    colors: SimpleListItemColors = SimpleListItemStyle.LazyTree.dark(),
    metrics: LazyTreeMetrics = LazyTreeMetrics.defaults(),
    icons: LazyTreeIcons = LazyTreeIcons.defaults(),
): LazyTreeStyle = LazyTreeStyle(colors, metrics, icons)

public fun LazyTreeMetrics.Companion.defaults(
    indentSize: Dp = 7.dp + 16.dp,
    elementBackgroundCornerSize: CornerSize = CornerSize(2.dp),
    elementPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    elementContentPadding: PaddingValues = PaddingValues(4.dp),
    elementMinHeight: Dp = 24.dp,
    chevronContentGap: Dp = 2.dp,
): LazyTreeMetrics =
    LazyTreeMetrics(
        indentSize = indentSize,
        chevronContentGap = chevronContentGap,
        elementMinHeight = elementMinHeight,
        simpleListItemMetrics =
            SimpleListItemMetrics(
                innerPadding = elementContentPadding,
                outerPadding = elementPadding,
                selectionBackgroundCornerSize = elementBackgroundCornerSize,
            ),
    )

public fun LazyTreeIcons.Companion.defaults(
    chevronCollapsed: IconKey = AllIconsKeys.General.ChevronRight,
    chevronExpanded: IconKey = AllIconsKeys.General.ChevronDown,
    chevronSelectedCollapsed: IconKey = chevronCollapsed,
    chevronSelectedExpanded: IconKey = chevronExpanded,
): LazyTreeIcons = LazyTreeIcons(chevronCollapsed, chevronExpanded, chevronSelectedCollapsed, chevronSelectedExpanded)
