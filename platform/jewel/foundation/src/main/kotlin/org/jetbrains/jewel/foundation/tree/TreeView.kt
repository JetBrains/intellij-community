package org.jetbrains.jewel.foundation.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyItemScope
import org.jetbrains.jewel.foundation.utils.Log

/**
 * A composable that displays a tree-like structure of elements in a hierarchical manner.
 *
 * @param modifier The modifier to apply to this layout.
 * @param tree The tree structure to be displayed.
 * @param treeState The state object that holds the state information for the tree view.
 * @param onElementClick The callback to be invoked when an element is clicked.
 * @param onElementDoubleClick The callback to be invoked when an element is double-clicked.
 * @param interactionSource The interaction source for handling user input events.
 * @param deepIndentDP The depth of indentation for nested elements in the tree view, in density-independent pixels.
 * @param focusedBackgroundColor The background color to be applied to the focused element.
 * @param selectionFocusedBackgroundColor The background color to be applied to the focused and selected element.
 * @param selectionBackgroundColor The background color to be applied to the selected element.
 * @param platformDoubleClickDelay The delay duration in milliseconds for registering a double-click event based on the platform's behavior.
 * @param keyActions The key binding actions for handling keyboard events.
 * @param pointerEventScopedActions The pointer event actions for handling pointer events.
 * @param arrowContent The content to be displayed for the expand/collapse arrow of each tree node,
 * specified as a lambda function with an `isOpen` parameter.
 * @param elementContent The content to be displayed for each tree element, specified as a lambda function
 * with a [SelectableLazyItemScope] receiver and a `Tree.Element<T>` parameter.
 *
 * @param T The type of data held by each tree element.
 */
@Suppress("UNCHECKED_CAST")
@Composable
fun <T> TreeView(
    modifier: Modifier = Modifier,
    tree: Tree<T>,
    treeState: TreeState = rememberTreeState(),
    onElementClick: (Tree.Element<T>) -> Unit = { Log.d("click") },
    onElementDoubleClick: (Tree.Element<T>) -> Unit = { Log.d("double click") },
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    deepIndentDP: Dp = 20.dp,
    focusedBackgroundColor: Color = Color.LightGray, // this will be changed with border brush
    selectionFocusedBackgroundColor: Color,
    selectionBackgroundColor: Color,
    platformDoubleClickDelay: Long = 500L,
    keyActions: KeyBindingScopedActions = DefaultTreeViewKeyActions(treeState),
    pointerEventScopedActions: PointerEventScopedActions = remember {
        DefaultTreeViewPointerEventAction(
            treeState,
            platformDoubleClickDelay,
            onElementClick,
            onElementDoubleClick
        )
    },
    arrowContent: @Composable (isOpen: Boolean) -> Unit,
    elementContent: @Composable SelectableLazyItemScope.(Tree.Element<T>) -> Unit
) {
    LaunchedEffect(tree) {
        treeState.attachTree(tree)
    }

    Log.w("selected: ${treeState.delegate.selectedItemIndexes}")
    Log.w("lastFocused= ${treeState.delegate.lastFocusedIndex}")

    val scope = rememberCoroutineScope()
    var isTreeFocused by remember { mutableStateOf(false) }

    SelectableLazyColumn(
        modifier.focusable().onFocusChanged { isTreeFocused = it.hasFocus },
        state = treeState.delegate,
        keyActions = keyActions,
        interactionSource = interactionSource,
        pointerHandlingScopedActions = pointerEventScopedActions
    ) {
        items(
            count = treeState.flattenedTree.size,
            key = {
                val idPath = treeState.flattenedTree[it].idPath()
                Log.t(idPath.toString())
                idPath
            },
            contentType = { treeState.flattenedTree[it].data }
        ) { itemIndex ->
            val element = treeState.flattenedTree[itemIndex]
            if (isSelected) {
                Log.e(
                    "I'm selected! " +
                        "my position in list is $itemIndex and my value is ${treeState.flattenedTree[itemIndex].data}"
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        when {
                            isFocused && !isSelected -> focusedBackgroundColor
                            isSelected && !isFocused -> selectionBackgroundColor
                            isFocused && isSelected -> selectionFocusedBackgroundColor
                            else -> Color.Unspecified
                        }
                    )
            ) {
                when (element) {
                    is Tree.Element.Leaf -> {
                        Box(modifier = Modifier.alpha(0f).width((element.depth * deepIndentDP.value).dp))
                        elementContent(element as Tree.Element<T>)
                    }

                    is Tree.Element.Node -> {
                        Box(modifier = Modifier.alpha(0f).width((element.depth * deepIndentDP.value).dp))
                        Box(
                            modifier = Modifier.alpha(if (element.children?.isEmpty() == true) 0f else 1f)
                                .pointerInput(Unit) {
                                    while (true) {
                                        awaitPointerEventScope {
                                            awaitFirstDown(false)
                                            scope.launch {
                                                treeState.toggleNode(element)
                                                onElementDoubleClick(element as Tree.Element<T>)
                                            }
                                        }
                                    }
                                }
                        ) {
                            arrowContent(treeState.isNodeOpen(element))
                        }
                        elementContent(element as Tree.Element<T>)
                    }
                }
            }
        }
    }
}
