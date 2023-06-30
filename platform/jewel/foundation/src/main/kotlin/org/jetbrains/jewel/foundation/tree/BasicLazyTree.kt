package org.jetbrains.jewel.foundation.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyItemScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A composable that displays a tree-like structure of elements in a hierarchical manner.
 *
 * @param modifier The modifier to apply to this layout.
 * @param tree The tree structure to be displayed.
 * @param treeState The state object that holds the state information for the tree view.
 * @param onElementClick The callback to be invoked when an element is clicked.
 * @param onElementDoubleClick The callback to be invoked when an element is double-clicked.
 * @param interactionSource The interaction source for handling user input events.
 * @param indentSize The depth of indentation for nested elements in the tree view, in density-independent pixels.
 * @param elementBackgroundFocused The background color to be applied to the focused, but not selected, element.
 * @param elementBackgroundSelectedFocused The background color to be applied to the focused and selected element.
 * @param elementBackgroundSelected The background color to be applied to the selected, but not focused, element.
 * @param platformDoubleClickDelay The delay duration in milliseconds for registering a double-click event based on the platform's behavior.
 * @param keyActions The key binding actions for handling keyboard events.
 * @param pointerEventScopedActions The pointer event actions for handling pointer events.
 * @param chevronContent The content to be displayed for the expand/collapse arrow of each tree node
 * @param nodeContent The content to be displayed for each tree element
 * with a [SelectableLazyItemScope] receiver and a `Tree.Element<T>` parameter.
 *
 * @param T The type of data held by each tree element.
 */
@Suppress("UNCHECKED_CAST")
@Composable
fun <T> BasicLazyTree(
    tree: Tree<T>,
    onElementClick: (Tree.Element<T>) -> Unit,
    elementBackgroundFocused: Color,
    elementBackgroundSelectedFocused: Color,
    elementBackgroundSelected: Color,
    indentSize: Dp,
    elementBackgroundCornerSize: CornerSize,
    elementPadding: PaddingValues,
    elementContentPadding: PaddingValues,
    elementMinHeight: Dp,
    chevronContentGap: Dp,
    treeState: TreeState = rememberTreeState(),
    modifier: Modifier = Modifier,
    onElementDoubleClick: (Tree.Element<T>) -> Unit = { },
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    platformDoubleClickDelay: Duration = 500.milliseconds,
    keyActions: KeyBindingScopedActions = DefaultTreeViewKeyActions(treeState),
    pointerEventScopedActions: PointerEventScopedActions = remember {
        DefaultTreeViewPointerEventAction(
            treeState,
            platformDoubleClickDelay.inWholeMilliseconds,
            onElementClick,
            onElementDoubleClick
        )
    },
    chevronContent: @Composable (nodeState: TreeElementState) -> Unit,
    nodeContent: @Composable SelectableLazyItemScope.(Tree.Element<T>) -> Unit
) {
    LaunchedEffect(tree) {
        treeState.attachTree(tree)
    }

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
                idPath
            },
            contentType = { treeState.flattenedTree[it].data }
        ) { itemIndex ->
            val element = treeState.flattenedTree[itemIndex]
            val elementState = TreeElementState.of(
                focused = isFocused,
                selected = isSelected,
                expanded = (element as? Tree.Element.Node)?.let { treeState.isNodeOpen(element) } ?: false
            )

            val backgroundShape by remember { mutableStateOf(RoundedCornerShape(elementBackgroundCornerSize)) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.defaultMinSize(minHeight = elementMinHeight)
                    .padding(elementPadding)
                    .elementBackground(
                        elementState,
                        elementBackgroundSelectedFocused,
                        elementBackgroundFocused,
                        elementBackgroundSelected,
                        backgroundShape
                    )
                    .padding(elementContentPadding)
                    .padding(start = (element.depth * indentSize.value).dp)
            ) {
                if (element is Tree.Element.Node) {
                    Box(
                        modifier = Modifier.pointerInput(Unit) {
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
                        chevronContent(elementState)
                    }
                    Spacer(Modifier.width(chevronContentGap))
                }
                nodeContent(element as Tree.Element<T>)
            }
        }
    }
}

private fun Modifier.elementBackground(
    state: TreeElementState,
    selectedFocused: Color,
    focused: Color,
    selected: Color,
    backgroundShape: RoundedCornerShape
) =
    background(
        color = when {
            state.isFocused && state.isSelected -> selectedFocused
            state.isFocused && !state.isSelected -> focused
            state.isSelected && !state.isFocused -> selected
            else -> Color.Unspecified
        },
        shape = backgroundShape
    )

@Immutable
@JvmInline
value class TreeElementState(val state: ULong) {

    @Stable
    val isFocused: Boolean
        get() = state and Focused != 0UL

    @Stable
    val isSelected: Boolean
        get() = state and Hovered != 0UL

    @Stable
    val isExpanded: Boolean
        get() = state and Expanded != 0UL

    fun copy(
        focused: Boolean = isFocused,
        selected: Boolean = isSelected,
        expanded: Boolean = isExpanded
    ) = of(focused, selected, expanded)

    override fun toString() =
        "${javaClass.simpleName}(isFocused=$isFocused, isSelected=$isSelected, isExpanded=$isExpanded)"

    companion object {

        private val Focused = 1UL shl 0
        private val Hovered = 1UL shl 1
        private val Pressed = 1UL shl 2
        private val Expanded = 1UL shl 3

        fun of(
            focused: Boolean,
            selected: Boolean,
            expanded: Boolean
        ) = TreeElementState(
            (if (focused) Focused else 0UL) or
                (if (selected) Pressed else 0UL) or
                (if (expanded) Expanded else 0UL)
        )
    }
}
