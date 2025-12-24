// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.SelectableLazyItemScope
import org.jetbrains.jewel.foundation.lazy.tree.DefaultTreeViewKeyActions
import org.jetbrains.jewel.foundation.lazy.tree.KeyActions
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.jetbrains.jewel.foundation.lazy.tree.rememberTreeState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.LazyTree
import org.jetbrains.jewel.ui.component.ProvideSearchMatchState
import org.jetbrains.jewel.ui.component.SpeedSearchScope
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.ui.theme.treeStyle

/**
 * Renders a lazy tree view with integrated speed search functionality.
 *
 * This composable combines [LazyTree] with speed search capabilities, providing automatic text matching, navigation
 * between matches, and intelligent scrolling behavior. It must be used within a [SpeedSearchScope] (typically provided
 * by [org.jetbrains.jewel.ui.component.SpeedSearchArea]).
 *
 * **Key features:**
 * - **Automatic matching**: Nodes are automatically matched against the search query based on their text content
 * - **Smart navigation**: Arrow keys navigate between matching nodes when search is active
 * - **Auto-scrolling**: Automatically scrolls to keep matching nodes visible
 * - **Selection integration**: Integrates selection state with search results
 * - **Performance optimized**: Uses a background dispatcher for search operations
 *
 * Example usage:
 * ```kotlin
 * SpeedSearchArea {
 *     SpeedSearchableTree(
 *         tree = myTree,
 *         nodeText = { it.data.name },
 *         modifier = Modifier.focusable(),
 *     ) { element ->
 *         var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
 *         SimpleListItem(
 *             text = element.data.name.highlightTextSearch(),
 *             selected = isSelected,
 *             active = isActive,
 *             onTextLayout = { textLayoutResult = it },
 *             textModifier = Modifier.highlightSpeedSearchMatches(textLayoutResult),
 *         )
 *     }
 * }
 * ```
 *
 * @param tree The tree structure to be rendered.
 * @param nodeText A function that extracts searchable text from a tree element, or `null` if the node should not be
 *   matched.
 * @param modifier The [Modifier] to apply to the tree component.
 * @param treeState The state object that controls tree expansion and selection.
 * @param onElementClick Called when a tree element is clicked.
 * @param onElementDoubleClick Called when a tree element is double-clicked.
 * @param onSelectionChange Called when the selection changes, providing the list of selected elements.
 * @param keyActions The key action handlers for keyboard navigation and interaction.
 * @param style The visual style configuration for the tree.
 * @param dispatcher The coroutine dispatcher used for background search operations. Defaults to [Dispatchers.Default].
 * @param nodeContent The composable used to render each tree node.
 * @see org.jetbrains.jewel.ui.component.SpeedSearchArea for the search container
 * @see highlightTextSearch for highlighting search matches in text
 * @see highlightSpeedSearchMatches for applying match highlight styles
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun <T> SpeedSearchScope.SpeedSearchableTree(
    tree: Tree<T>,
    nodeText: (Tree.Element<T>) -> String?,
    modifier: Modifier = Modifier,
    treeState: TreeState = rememberTreeState(),
    onElementClick: (Tree.Element<T>) -> Unit = {},
    onElementDoubleClick: (Tree.Element<T>) -> Unit = {},
    onSelectionChange: (List<Tree.Element<T>>) -> Unit = {},
    keyActions: KeyActions = DefaultTreeViewKeyActions(treeState),
    style: LazyTreeStyle = JewelTheme.treeStyle,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    nodeContent: @Composable (SelectableLazyItemScope.(Tree.Element<T>) -> Unit),
) {
    val currentTree = rememberUpdatedState(tree)
    val currentNodeText = rememberUpdatedState(nodeText)

    val currentTreeToList = remember {
        derivedStateOf { currentTree.value.toList(currentNodeText.value, treeState.openNodes) }
    }

    val speedSearchKeyActions =
        remember(keyActions) { SpeedSearchableLazyColumnKeyActions(keyActions, speedSearchState) }

    LazyTree(
        tree = tree,
        modifier = modifier.onPreviewKeyEvent(::processKeyEvent),
        onElementClick = onElementClick,
        treeState = treeState,
        onElementDoubleClick = onElementDoubleClick,
        onSelectionChange = onSelectionChange,
        keyActions = speedSearchKeyActions,
        style = style,
        interactionSource = interactionSource,
    ) {
        ProvideSearchMatchState(speedSearchState, nodeText(it), searchMatchStyle) { nodeContent(it) }
    }

    SpeedSearchableLazyColumnScrollEffect(
        treeState.lazyListState,
        speedSearchState,
        currentTreeToList.value.second,
        dispatcher,
    )

    LaunchedEffect(dispatcher) {
        val entriesState = MutableStateFlow(emptyList<String?>())

        val entriesFlow = snapshotFlow { currentTreeToList.value.first }
        async(dispatcher) { entriesFlow.collect(entriesState::emit) }

        speedSearchState.attach(entriesState, dispatcher)
    }
}

private fun <T> Tree<T>.toList(
    nodeText: (Tree.Element<T>) -> String?,
    openNodes: Set<Any>,
): Pair<List<String?>, List<Any?>> {
    val texts = mutableListOf<String?>()
    val keys = mutableListOf<Any?>()

    val nodes = walkDepthFirst().filter { it.parent == null || it.parent?.id in openNodes }
    for (node in nodes) {
        texts.add(nodeText(node))
        keys.add(node.id)
    }

    return (texts to keys)
}
