package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.SelectableLazyItemScope
import org.jetbrains.jewel.foundation.lazy.tree.BasicLazyTree
import org.jetbrains.jewel.foundation.lazy.tree.DefaultTreeViewKeyActions
import org.jetbrains.jewel.foundation.lazy.tree.KeyActions
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeElementState
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.jetbrains.jewel.foundation.lazy.tree.rememberTreeState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.contentFor
import org.jetbrains.jewel.ui.theme.treeStyle

@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun <T> LazyTree(
    tree: Tree<T>,
    modifier: Modifier = Modifier,
    onElementClick: (Tree.Element<T>) -> Unit = {},
    treeState: TreeState = rememberTreeState(),
    onElementDoubleClick: (Tree.Element<T>) -> Unit = {},
    onSelectionChange: (List<Tree.Element<T>>) -> Unit = {},
    keyActions: KeyActions = DefaultTreeViewKeyActions(treeState),
    style: LazyTreeStyle = JewelTheme.treeStyle,
    nodeContent: @Composable (SelectableLazyItemScope.(Tree.Element<T>) -> Unit),
) {
    val colors = style.colors
    val metrics = style.metrics

    BasicLazyTree(
        tree = tree,
        onElementClick = onElementClick,
        elementBackgroundFocused = colors.backgroundActive,
        elementBackgroundSelectedFocused = colors.backgroundSelectedActive,
        elementBackgroundSelected = colors.backgroundSelected,
        indentSize = metrics.indentSize,
        elementBackgroundCornerSize = metrics.simpleListItemMetrics.selectionBackgroundCornerSize,
        elementPadding = metrics.simpleListItemMetrics.outerPadding,
        elementContentPadding = metrics.simpleListItemMetrics.innerPadding,
        elementMinHeight = metrics.elementMinHeight,
        chevronContentGap = metrics.chevronContentGap,
        treeState = treeState,
        modifier = modifier,
        onElementDoubleClick = onElementDoubleClick,
        onSelectionChange = onSelectionChange,
        keyActions = keyActions,
        chevronContent = { elementState ->
            val iconKey = style.icons.chevron(elementState.isExpanded, elementState.isSelected)
            Icon(iconKey, contentDescription = null)
        },
    ) {
        val resolvedContentColor =
            style.colors
                .contentFor(TreeElementState.of(focused = isActive, selected = isSelected, expanded = false))
                .value
                .takeOrElse { LocalContentColor.current }

        CompositionLocalProvider(LocalContentColor provides resolvedContentColor) { nodeContent(it) }
    }
}
