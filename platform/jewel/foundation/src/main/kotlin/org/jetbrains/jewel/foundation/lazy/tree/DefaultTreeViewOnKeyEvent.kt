package org.jetbrains.jewel.foundation.lazy.tree

import org.jetbrains.jewel.foundation.lazy.SelectableLazyListKey
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState

public open class DefaultTreeViewOnKeyEvent(
    override val keybindings: TreeViewKeybindings,
    private val treeState: TreeState,
) : TreeViewOnKeyEvent {
    override fun onSelectParent(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        val currentKey = keys[state.lastActiveItemIndex ?: 0].key
        val keyNodeList = treeState.allNodes.map { it.first }

        // If it's a node and it's expanded, collapse it
        if (currentKey in keyNodeList && treeState.openNodes.contains(currentKey)) {
            treeState.toggleNode(currentKey)
            return
        }

        // For leaf nodes or collapsed nodes, move to the previous item
        super.onSelectPreviousItem(keys, state)
    }

    override fun onSelectChild(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        val currentKey = keys[state.lastActiveItemIndex ?: 0].key
        if (currentKey in treeState.allNodes.map { it.first } && currentKey !in treeState.openNodes) {
            treeState.toggleNode(currentKey)
        } else {
            super.onSelectNextItem(keys, state)
        }
    }
}
