package org.jetbrains.jewel.foundation.lazy.tree

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectableScope

/**
 * Creates and remembers a [TreeState] backed by [selectableLazyListState].
 *
 * @param lazyListState The [LazyListState] used for scroll and layout. Defaults to a new instance.
 * @param selectableLazyListState The selectable list state backing the tree. Defaults to a new instance wrapping
 *   [lazyListState].
 */
@Composable
public fun rememberTreeState(
    lazyListState: LazyListState = LazyListState(),
    selectableLazyListState: SelectableLazyListState = SelectableLazyListState(lazyListState),
): TreeState = remember { TreeState(selectableLazyListState) }

/** Holds the runtime state of a tree view: the set of open node IDs and the underlying selectable list state. */
public class TreeState(delegate: SelectableLazyListState) : SelectableScope by delegate, ScrollableState by delegate {
    internal val allNodes = mutableStateListOf<Pair<Any, Int>>()

    /** The underlying selectable lazy list state backing the tree. */
    @InternalJewelApi @ApiStatus.Internal public val lazyListState: SelectableLazyListState = delegate

    /** The set of node IDs that are currently expanded (open) in the tree. */
    public var openNodes: Set<Any> by mutableStateOf<Set<Any>>(emptySet())

    /**
     * Toggles the open/closed state of the node with [nodeId]: removes it from [openNodes] if present, or adds it
     * otherwise.
     *
     * @param nodeId The unique identifier of the node to toggle.
     */
    public fun toggleNode(nodeId: Any) {
        if (nodeId in openNodes) {
            openNodes -= nodeId
        } else {
            openNodes += nodeId
        }
    }

    /**
     * Adds all [nodes] to [openNodes], expanding them all at once.
     *
     * @param nodes The list of node IDs to open.
     */
    public fun openNodes(nodes: List<Any>) {
        openNodes += nodes
    }
}
