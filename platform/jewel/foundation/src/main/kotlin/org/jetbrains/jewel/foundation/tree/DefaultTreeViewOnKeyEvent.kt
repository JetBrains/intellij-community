package org.jetbrains.jewel.foundation.tree

import org.jetbrains.jewel.foundation.utils.Log
import kotlin.math.max
import kotlin.math.min

open class DefaultTreeViewOnKeyEvent(
    override val keybindings: TreeViewKeybindings,
    private val treeState: TreeState,
    private val animate: Boolean = false,
    private val scrollOffset: Int = 0,
) : TreeViewOnKeyEvent {

    override suspend fun onSelectFirstItem() {
        Log.e(treeState.toString())
        if (treeState.delegate.keys.isNotEmpty()) treeState.selectSingleElement(0)
    }

    override suspend fun onExtendSelectionToFirst(currentIndex: Int) {
        if (treeState.delegate.keys.isNotEmpty()) {
            treeState.addElementsToSelection((0..currentIndex).toList().reversed())
        }
    }

    override suspend fun onSelectLastItem() {
        treeState.delegate.keys.lastIndex.takeIf { it >= 0 }?.let {
            treeState.selectSingleElement(it)
        }
    }

    override suspend fun onExtendSelectionToLastItem(currentIndex: Int) {
        if (treeState.delegate.keys.isNotEmpty()) {
            treeState.addElementsToSelection((currentIndex..treeState.delegate.keys.lastIndex).toList())
        }
    }

    override suspend fun onSelectPreviousItem(currentIndex: Int) {
        treeState.delegate.keys.getOrNull(currentIndex - 1)?.let {
            treeState.selectSingleElement(currentIndex - 1)
        }
    }

    override suspend fun onExtendSelectionWithPreviousItem(currentIndex: Int) {
        val prevIndex = currentIndex - 1

        if (treeState.delegate.keys.isNotEmpty() && prevIndex >= 0) {
            if (treeState.lastKeyEventUsedMouse) {
                if (treeState.delegate.selectedItemIndexes.contains(prevIndex)) {
                    // we are are changing direction so we needs just deselect the current element
                    treeState.deselectElement(currentIndex, false)
                } else {
                    treeState.addElementToSelection(prevIndex, false)
                }
            } else {
                treeState.deselectAll()
                treeState.addElementsToSelection(
                    listOf(
                        currentIndex,
                        prevIndex
                    ),
                    null
                )
                treeState.lastKeyEventUsedMouse = true
            }
        }
        if (prevIndex >= 0) treeState.delegate.focusItem(prevIndex)
    }

    override suspend fun onSelectNextItem(currentIndex: Int) {
        if (treeState.delegate.keys.size > currentIndex + 1) {
            treeState.selectSingleElement(currentIndex + 1)
        }
    }

    override suspend fun onExtendSelectionWithNextItem(currentIndex: Int) {
        val nextFlattenIndex = currentIndex + 1

        if (treeState.delegate.keys.isNotEmpty() && nextFlattenIndex <= treeState.delegate.keys.lastIndex) {
            if (treeState.lastKeyEventUsedMouse) {
                if (treeState.delegate.selectedItemIndexes.contains(nextFlattenIndex)) {
                    // we are are changing direction so we needs just deselect the current element
                    treeState.deselectElement(currentIndex)
                } else {
                    treeState.addElementToSelection(nextFlattenIndex, false)
                }
            } else {
                treeState.deselectAll()
                treeState.addElementsToSelection(
                    listOf(
                        currentIndex,
                        nextFlattenIndex
                    ),
                    null
                )
                treeState.lastKeyEventUsedMouse = true
            }
            treeState.delegate.focusItem(nextFlattenIndex)
        }
    }

    override suspend fun onSelectParent(flattenedIndex: Int) {
        val currentKey = treeState.delegate.keys[flattenedIndex].key

        if (currentKey in treeState.allNodes && currentKey in treeState.openNodes) {
            treeState.toggleNode(currentKey)
        } else {
            onSelectPreviousItem(flattenedIndex)
        }
    }

    override suspend fun onSelectChild(flattenedIndex: Int) {
        val currentKey = treeState.delegate.keys[flattenedIndex].key
        if (currentKey in treeState.allNodes && currentKey !in treeState.openNodes) {
            treeState.toggleNode(currentKey)
        } else {
            onSelectNextItem(flattenedIndex)
        }
    }

    override suspend fun onScrollPageUpAndSelectItem(currentIndex: Int) {
        val visibleSize = treeState.delegate.layoutInfo.visibleItemsInfo.size
        val targetIndex = max(currentIndex - visibleSize, 0)
        treeState.selectSingleElement(targetIndex)
    }

    override suspend fun onScrollPageUpAndExtendSelection(currentIndex: Int) {
        val visibleSize = treeState.delegate.layoutInfo.visibleItemsInfo.size
        val targetIndex = max(currentIndex - visibleSize, 0)
        for (i in targetIndex..currentIndex) treeState.addElementToSelection(i)
        treeState.delegate.focusItem(targetIndex, animate, scrollOffset)
    }

    override suspend fun onScrollPageDownAndSelectItem(currentIndex: Int) {
        val firstVisible = treeState.delegate.firstVisibleItemIndex
        val visibleSize = treeState.delegate.layoutInfo.visibleItemsInfo.size
        val targetIndex = min(firstVisible + visibleSize, treeState.delegate.keys.lastIndex)
        treeState.selectSingleElement(targetIndex)
    }

    override suspend fun onScrollPageDownAndExtendSelection(currentIndex: Int) {
        val firstVisible = treeState.delegate.firstVisibleItemIndex
        val visibleSize = treeState.delegate.layoutInfo.visibleItemsInfo.size
        val targetIndex = min(firstVisible + visibleSize, treeState.delegate.keys.lastIndex)

        treeState.addElementsToSelection((currentIndex..targetIndex).toList(), targetIndex)

        treeState.delegate.focusItem(targetIndex, animate, scrollOffset)
    }

    override suspend fun onEdit(currentIndex: Int) {
        // ij with this shortcut just focus the first element with issue
        // unavailable here
    }
}
