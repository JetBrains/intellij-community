package org.jetbrains.jewel.foundation.lazy.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyItemScope
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.itemsIndexed
import org.jetbrains.jewel.foundation.state.CommonStateBitMask
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Enabled
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Focused
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Hovered
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Selected
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.state.SelectableComponentState

/**
 * Renders a lazy tree view based on the provided tree data structure.
 *
 * @param tree The tree structure to be rendered.
 * @param selectionMode The selection mode for the tree nodes.
 * @param onElementClick Callback function triggered when a tree node is clicked.
 * @param elementBackgroundFocused The background color of a tree node when focused.
 * @param elementBackgroundSelectedFocused The background color of a selected tree node when focused.
 * @param elementBackgroundSelected The background color of a selected tree node.
 * @param indentSize The size of the indent for each level of the tree node.
 * @param elementBackgroundCornerSize The corner size of the background shape of a tree node.
 * @param elementPadding The padding for the entire tree node.
 * @param elementContentPadding The padding for the content within a tree node.
 * @param elementMinHeight The minimum height of a tree node.
 * @param chevronContentGap The gap between the chevron icon and the node content.
 * @param treeState The state object for managing the tree view state.
 * @param modifier Optional modifier for styling or positioning the tree view.
 * @param onElementDoubleClick Callback function triggered when a tree node is double-clicked.
 * @param onSelectionChange Callback function triggered when the selected tree nodes change.
 * @param platformDoubleClickDelay The duration between two consecutive clicks to be considered a double-click.
 * @param keyActions The key binding actions for the tree view.
 * @param pointerEventScopedActions The pointer event actions for the tree view.
 * @param chevronContent The composable function responsible for rendering the chevron icon.
 * @param nodeContent The composable function responsible for rendering the content of a tree node.
 *
 * @suppress("UNCHECKED_CAST")
 *
 * @composable
 */
@Suppress("UNCHECKED_CAST")
@Composable
public fun <T> BasicLazyTree(
    tree: Tree<T>,
    selectionMode: SelectionMode = SelectionMode.Multiple,
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
    onElementDoubleClick: (Tree.Element<T>) -> Unit,
    onSelectionChange: (List<Tree.Element<T>>) -> Unit,
    platformDoubleClickDelay: Duration = 500.milliseconds,
    keyActions: KeyActions = DefaultTreeViewKeyActions(treeState),
    pointerEventScopedActions: PointerEventActions = remember { DefaultTreeViewPointerEventAction(treeState) },
    chevronContent: @Composable (nodeState: TreeElementState) -> Unit,
    nodeContent: @Composable (SelectableLazyItemScope.(Tree.Element<T>) -> Unit),
) {
    val scope = rememberCoroutineScope()

    val flattenedTree =
        remember(tree, treeState.openNodes, treeState.allNodes) { tree.roots.flatMap { it.flattenTree(treeState) } }

    remember(tree) { // if tree changes we need to update selection changes
        onSelectionChange(
            flattenedTree
                .asSequence()
                .filter { it.id in treeState.delegate.selectedKeys }
                .map { element -> element as Tree.Element<T> }
                .toList()
        )
    }

    SelectableLazyColumn(
        modifier = modifier,
        selectionMode = selectionMode,
        state = treeState.delegate,
        onSelectedIndexesChange = {
            onSelectionChange(it.map { element -> flattenedTree[element] as Tree.Element<T> })
        },
        keyActions = keyActions,
        pointerEventActions = pointerEventScopedActions,
    ) {
        itemsIndexed(items = flattenedTree, key = { _, item -> item.id }, contentType = { _, item -> item.data }) {
            index,
            element ->
            val elementState =
                TreeElementState.of(
                    active = isActive,
                    selected = isSelected,
                    expanded = (element as? Tree.Element.Node)?.let { it.id in treeState.openNodes } ?: false,
                )

            val backgroundShape by derivedStateOf {
                val hasRoundedTopCorners =
                    flattenedTree.getOrNull(index - 1)?.id?.let { it !in treeState.delegate.selectedKeys } ?: true
                val hasRoundedBottomCorners =
                    flattenedTree.getOrNull(index + 1)?.id?.let { it !in treeState.delegate.selectedKeys } ?: true
                val topCornerSize = computerCornerSize(hasRoundedTopCorners, elementBackgroundCornerSize)
                val bottomCornerSize = computerCornerSize(hasRoundedBottomCorners, elementBackgroundCornerSize)
                RoundedCornerShape(
                    topStart = topCornerSize,
                    topEnd = topCornerSize,
                    bottomEnd = bottomCornerSize,
                    bottomStart = bottomCornerSize,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.defaultMinSize(minHeight = elementMinHeight)
                        .padding(elementPadding)
                        .elementBackground(
                            state = elementState,
                            selectedFocused = elementBackgroundSelectedFocused,
                            focused = elementBackgroundFocused,
                            selected = elementBackgroundSelected,
                            backgroundShape = backgroundShape,
                        )
                        .padding(elementContentPadding)
                        .padding(start = (element.depth * indentSize.value).dp)
                        .semantics(mergeDescendants = true) {
                            // Only set expansion state in content description for nodes
                            if (element is Tree.Element.Node) {
                                contentDescription = if (element.id in treeState.openNodes) "expanded" else "collapsed"
                            }

                            selected = element.id in treeState.delegate.selectedKeys
                            focused = treeState.delegate.lastActiveItemIndex == index

                            stateDescription =
                                if (
                                    treeState.delegate.isKeyboardNavigating &&
                                        treeState.delegate.lastActiveItemIndex == index
                                ) {
                                    "current item"
                                } else {
                                    ""
                                }
                        }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            val item = flattenedTree[index] as Tree.Element<T>
                            (pointerEventScopedActions as? DefaultTreeViewPointerEventAction)?.notifyItemClicked(
                                item = item,
                                scope = scope,
                                doubleClickTimeDelayMillis = platformDoubleClickDelay.inWholeMilliseconds,
                                onElementClick = onElementClick,
                                onElementDoubleClick = onElementDoubleClick,
                            )
                            treeState.delegate.lastActiveItemIndex = index
                        },
            ) {
                if (element is Tree.Element.Node) {
                    Box(
                        modifier =
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                treeState.toggleNode(element.id)
                                onElementDoubleClick(element as Tree.Element<T>)
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

private fun computerCornerSize(isRounded: Boolean, cornerSize: CornerSize) =
    if (isRounded) cornerSize else CornerSize(0.dp)

private fun Modifier.elementBackground(
    state: TreeElementState,
    selectedFocused: Color,
    focused: Color,
    selected: Color,
    backgroundShape: RoundedCornerShape,
) =
    background(
        color =
            when {
                state.isActive && state.isSelected -> selectedFocused
                state.isActive && !state.isSelected -> focused
                state.isSelected && !state.isActive -> selected
                else -> Color.Unspecified
            },
        shape = backgroundShape,
    )

@Immutable
@JvmInline
public value class TreeElementState(public val state: ULong) : FocusableComponentState, SelectableComponentState {
    @Stable
    override val isActive: Boolean
        get() = state and Active != 0UL

    @Stable
    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    @Stable
    override val isFocused: Boolean
        get() = state and Focused != 0UL

    @Stable
    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    @Stable
    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    @Stable
    override val isSelected: Boolean
        get() = state and Selected != 0UL

    @Stable
    public val isExpanded: Boolean
        get() = state and Expanded != 0UL

    override fun toString(): String =
        "${javaClass.simpleName}(enabled=$isEnabled, focused=$isFocused, expanded=$isExpanded, " +
            "pressed=$isPressed, hovered=$isHovered, active=$isActive, selected=$isSelected)"

    public fun copy(
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        expanded: Boolean = isExpanded,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
        selected: Boolean = isSelected,
    ): TreeElementState =
        of(
            enabled = enabled,
            focused = focused,
            expanded = expanded,
            pressed = pressed,
            hovered = hovered,
            active = active,
            selected = selected,
        )

    public companion object {
        private const val EXPANDED_BIT_OFFSET = CommonStateBitMask.FIRST_AVAILABLE_OFFSET

        private val Expanded = 1UL shl EXPANDED_BIT_OFFSET

        public fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            expanded: Boolean = false,
            hovered: Boolean = false,
            pressed: Boolean = false,
            active: Boolean = false,
            selected: Boolean = false,
        ): TreeElementState =
            TreeElementState(
                (if (expanded) Expanded else 0UL) or
                    (if (enabled) Enabled else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (selected) Selected else 0UL) or
                    (if (active) Active else 0UL)
            )
    }
}

private fun Tree.Element<*>.flattenTree(state: TreeState): MutableList<Tree.Element<*>> {
    val orderedChildren = mutableListOf<Tree.Element<*>>()
    when (this) {
        is Tree.Element.Node<*> -> {
            if (id !in state.allNodes.map { it.first }) state.allNodes.add(id to depth)
            orderedChildren.add(this)
            if (id !in state.openNodes) {
                return orderedChildren.also {
                    close()
                    // remove all children key from openNodes
                    state.openNodes -= buildSet { getAllSubNodes(this@flattenTree) }
                }
            }
            open(true)
            children?.forEach { child -> orderedChildren.addAll(child.flattenTree(state)) }
        }
        is Tree.Element.Leaf<*> -> {
            orderedChildren.add(this)
        }
    }
    return orderedChildren
}

private infix fun MutableSet<Any>.getAllSubNodes(node: Tree.Element.Node<*>) {
    node.children?.filterIsInstance<Tree.Element.Node<*>>()?.forEach {
        add(it.id)
        this@getAllSubNodes getAllSubNodes (it)
    }
}
