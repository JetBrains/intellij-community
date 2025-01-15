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
        contentActive: Color = content,
        contentSelected: Color = content,
        contentSelectedActive: Color = content,
        nodeBackground: Color = Color.Unspecified,
        nodeBackgroundActive: Color = Color.Unspecified,
        nodeBackgroundSelected: Color = IntUiLightTheme.colors.gray(11),
        nodeBackgroundSelectedActive: Color = IntUiLightTheme.colors.blue(11),
    ): SimpleListItemColors =
        SimpleListItemColors(
            background = nodeBackground,
            backgroundActive = nodeBackgroundActive,
            backgroundSelected = nodeBackgroundSelected,
            backgroundSelectedActive = nodeBackgroundSelectedActive,
            content = content,
            contentActive = contentActive,
            contentSelected = contentSelected,
            contentSelectedActive = contentSelectedActive,
        )

    @Composable
    public fun dark(
        content: Color = Color.Unspecified,
        contentActive: Color = content,
        contentSelected: Color = content,
        contentSelectedActive: Color = content,
        nodeBackground: Color = Color.Unspecified,
        nodeBackgroundActive: Color = Color.Unspecified,
        nodeBackgroundSelected: Color = IntUiDarkTheme.colors.gray(4),
        nodeBackgroundSelectedActive: Color = IntUiDarkTheme.colors.blue(2),
    ): SimpleListItemColors =
        SimpleListItemColors(
            background = nodeBackground,
            backgroundActive = nodeBackgroundActive,
            backgroundSelected = nodeBackgroundSelected,
            backgroundSelectedActive = nodeBackgroundSelectedActive,
            content = content,
            contentActive = contentActive,
            contentSelected = contentSelected,
            contentSelectedActive = contentSelectedActive,
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
    elementIconTextGap: Dp = 4.dp,
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
                iconTextGap = elementIconTextGap,
            ),
    )

public fun LazyTreeIcons.Companion.defaults(
    chevronCollapsed: IconKey = AllIconsKeys.General.ChevronRight,
    chevronExpanded: IconKey = AllIconsKeys.General.ChevronDown,
    chevronSelectedCollapsed: IconKey = chevronCollapsed,
    chevronSelectedExpanded: IconKey = chevronExpanded,
): LazyTreeIcons = LazyTreeIcons(chevronCollapsed, chevronExpanded, chevronSelectedCollapsed, chevronSelectedExpanded)
