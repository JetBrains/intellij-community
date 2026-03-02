// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.jewel.foundation.lazy.tree.DefaultTreeViewPointerEventAction
import org.jetbrains.jewel.foundation.lazy.tree.KeyActions
import org.jetbrains.jewel.foundation.lazy.tree.PointerEventActions
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
 * @param tree The tree structure to be rendered.
 * @param nodeText A function that extracts displayable text from tree elements for search matching.
 * @param modifier The [Modifier] to apply to the tree component.
 * @param treeState The state object that controls tree expansion and selection.
 * @param onElementClick Callback function triggered when a tree element is clicked.
 * @param onElementDoubleClick Callback function triggered when a tree element is double-clicked.
 * @param onSelectionChange Callback function triggered when the selection changes, providing the list of selected
 *   elements.
 * @param keyActions The key action handlers for keyboard navigation and interaction.
 * @param style The visual style configuration for the tree.
 * @param dispatcher The coroutine dispatcher used for background search operations.
 * @param nodeContent The composable function responsible for rendering the content of tree nodes.
 * @composable
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
    pointerEventActions: PointerEventActions = DefaultTreeViewPointerEventAction(treeState),
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
        pointerEventActions = pointerEventActions,
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
