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

@Composable
public fun rememberTreeState(
    lazyListState: LazyListState = LazyListState(),
    selectableLazyListState: SelectableLazyListState = SelectableLazyListState(lazyListState),
): TreeState = remember { TreeState(selectableLazyListState) }

public class TreeState(delegate: SelectableLazyListState) : SelectableScope by delegate, ScrollableState by delegate {
    internal val allNodes = mutableStateListOf<Pair<Any, Int>>()

    @InternalJewelApi @ApiStatus.Internal public val lazyListState: SelectableLazyListState = delegate

    public var openNodes: Set<Any> by mutableStateOf<Set<Any>>(emptySet())

    public fun toggleNode(nodeId: Any) {
        if (nodeId in openNodes) {
            openNodes -= nodeId
        } else {
            openNodes += nodeId
        }
    }

    public fun openNodes(nodes: List<Any>) {
        openNodes += nodes
    }
}
