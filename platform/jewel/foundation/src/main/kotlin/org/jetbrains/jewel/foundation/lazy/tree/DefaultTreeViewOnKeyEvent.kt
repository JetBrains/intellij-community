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

        if (currentKey !in keyNodeList) {
            handleLeafCase(keys, currentKey, keyNodeList, state)
        } else {
            handleNodeCase(currentKey, keys, state)
        }
    }

    private fun handleNodeCase(currentKey: Any, keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        if (treeState.openNodes.contains(currentKey)) {
            treeState.toggleNode(currentKey)
            return
        }
        treeState.allNodes
            .first { it.first == currentKey }
            .let { currentNode ->
                treeState.allNodes
                    .subList(0, treeState.allNodes.indexOf(currentNode))
                    .reversed()
                    .firstOrNull { it.second < currentNode.second }
                    ?.let { (parentNodeKey, _) ->
                        keys
                            .first { it.key == parentNodeKey }
                            .takeIf { it is SelectableLazyListKey.Selectable }
                            ?.let {
                                state.lastActiveItemIndex =
                                    keys.indexOfFirst { selectableKey -> selectableKey.key == parentNodeKey }
                                state.selectedKeys = setOf(parentNodeKey)
                            }
                    }
            }
    }

    private fun handleLeafCase(
        keys: List<SelectableLazyListKey>,
        currentKey: Any,
        keyNodeList: List<Any>,
        state: SelectableLazyListState,
    ) {
        val index = keys.indexOf(currentKey)
        if (index < 0) return
        for (i in index downTo 0) {
            if (keys[i].key in keyNodeList) {
                if (keys[i] is SelectableLazyListKey.Selectable) {
                    state.lastActiveItemIndex = i
                    state.selectedKeys = setOf(keys[i].key)
                }
                break
            }
        }
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
