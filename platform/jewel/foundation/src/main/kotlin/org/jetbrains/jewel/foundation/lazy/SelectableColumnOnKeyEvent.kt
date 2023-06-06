package org.jetbrains.jewel.foundation.lazy

import kotlin.math.max
import kotlin.math.min

interface SelectableColumnOnKeyEvent {

    val keybindings: SelectableColumnKeybindings

    /**
     * Select First Node
     */
    suspend fun onSelectFirstItem()

    /**
     * Extend Selection to First Node inherited from Move Caret to Text Start with Selection
     */
    suspend fun onExtendSelectionToFirst(currentIndex: Int)

    /**
     * Select Last Node inherited from Move Caret to Text End
     */
    suspend fun onSelectLastItem()

    /**
     * Extend Selection to Last Node inherited from Move Caret to Text End with Selection
     */
    suspend fun onExtendSelectionToLastItem(currentIndex: Int)

    /**
     * Select Previous Node inherited from Up
     */
    suspend fun onSelectPreviousItem(currentIndex: Int)

    /**
     * Extend Selection with Previous Node inherited from Up with Selection
     */
    suspend fun onExtendSelectionWithPreviousItem(currentIndex: Int)

    /**
     * Select Next Node inherited from Down
     */
    suspend fun onSelectNextItem(currentIndex: Int)

    /**
     * Extend Selection with Next Node inherited from Down with Selection
     */
    suspend fun onExtendSelectionWithNextItem(currentIndex: Int)

    /**
     * Scroll Page Up and Select Node inherited from Page Up
     */
    suspend fun onScrollPageUpAndSelectItem(currentIndex: Int)

    /**
     * Scroll Page Up and Extend Selection inherited from Page Up with Selection
     */
    suspend fun onScrollPageUpAndExtendSelection(currentIndex: Int)

    /**
     * Scroll Page Down and Select Node inherited from Page Down
     */
    suspend fun onScrollPageDownAndSelectItem(currentIndex: Int)

    /**
     * Scroll Page Down and Extend Selection inherited from Page Down with Selection
     */
    suspend fun onScrollPageDownAndExtendSelection(currentIndex: Int)

    /**
     * Edit In Item
     */
    suspend fun onEdit(currentIndex: Int)
}

open class DefaultSelectableOnKeyEvent(
    override val keybindings: SelectableColumnKeybindings,
    private val selectableState: SelectableLazyListState
) : SelectableColumnOnKeyEvent {

    override suspend fun onSelectFirstItem() {
        val firstSelectable = selectableState.keys.indexOfFirst { it.selectable }
        if (firstSelectable >= 0) selectableState.selectSingleItem(firstSelectable)
    }

    override suspend fun onExtendSelectionToFirst(currentIndex: Int) {
        if (selectableState.keys.isNotEmpty()) {
            buildList {
                for (i in currentIndex downTo 0) {
                    if (selectableState.keys[i].selectable) add(i)
                }
            }.let {
                selectableState.addElementsToSelection(it, it.last())
            }
        }
    }

    override suspend fun onSelectLastItem() {
        val lastSelectable = selectableState.keys.indexOfLast { it.selectable }
        if (lastSelectable >= 0) selectableState.selectSingleItem(lastSelectable)
    }

    override suspend fun onExtendSelectionToLastItem(currentIndex: Int) {
        if (selectableState.keys.isNotEmpty()) {
            val lastKey = selectableState.keys.lastIndex
            buildList {
                for (i in currentIndex..lastKey) {
                    if (selectableState.keys[i].selectable) add(element = i)
                }
            }.let {
                selectableState.addElementsToSelection(it)
            }
        }
    }

    override suspend fun onSelectPreviousItem(currentIndex: Int) {
        if (currentIndex - 1 >= 0) {
            for (i in currentIndex - 1 downTo 0) {
                if (selectableState.keys[i].selectable) {
                    selectableState.selectSingleItem(i)
                    break
                }
            }
        }
    }

    override suspend fun onExtendSelectionWithPreviousItem(currentIndex: Int) {
        if (currentIndex - 1 >= 0) {
            val prevIndex = selectableState.indexOfPreviousSelectable(currentIndex) ?: return
            if (selectableState.lastKeyEventUsedMouse) {
                selectableState.selectedIdsMap.contains(selectableState.keys[currentIndex])
                if (selectableState.selectedIdsMap.contains(selectableState.keys[prevIndex])) {
                    selectableState.selectedIdsMap.remove(selectableState.keys[currentIndex])
                    selectableState.focusItem(prevIndex, animateScroll = false, 0)
                } else {
                    selectableState.addElementToSelection(prevIndex)
                }
            } else {
                selectableState.deselectAll()
                selectableState.addElementsToSelection(
                    listOf(
                        currentIndex,
                        prevIndex
                    )
                )
                selectableState.lastKeyEventUsedMouse = true
            }
        }
    }

    override suspend fun onSelectNextItem(currentIndex: Int) {
        selectableState.indexOfNextSelectable(currentIndex)?.let {
            selectableState.selectSingleItem(it)
        }
    }

    override suspend fun onExtendSelectionWithNextItem(currentIndex: Int) {
        val nextSelectableIndex = selectableState.indexOfNextSelectable(currentIndex)
        if (nextSelectableIndex != null) {
            if (selectableState.lastKeyEventUsedMouse) {
                if (selectableState.selectedIdsMap.contains(selectableState.keys[nextSelectableIndex])) {
                    selectableState.selectedIdsMap.remove(selectableState.keys[currentIndex])
                    selectableState.focusItem(nextSelectableIndex, false, 0)
                } else {
                    selectableState.addElementToSelection(nextSelectableIndex)
                }
            } else {
                selectableState.deselectAll()
                selectableState.addElementsToSelection(
                    listOf(
                        currentIndex,
                        nextSelectableIndex
                    )
                )
                selectableState.lastKeyEventUsedMouse = true
            }
        }
    }

    override suspend fun onScrollPageUpAndSelectItem(currentIndex: Int) {
        val visibleSize = selectableState.layoutInfo.visibleItemsInfo.size
        val targetIndex = max(currentIndex - visibleSize, 0)
        if (!selectableState.keys[targetIndex].selectable) {
            selectableState.indexOfPreviousSelectable(currentIndex) ?: selectableState.indexOfNextSelectable(currentIndex)?.let {
                selectableState.selectSingleItem(it)
            }
        } else {
            selectableState.selectSingleItem(targetIndex)
        }
    }

    override suspend fun onScrollPageUpAndExtendSelection(currentIndex: Int) {
        val visibleSize = selectableState.layoutInfo.visibleItemsInfo.size
        val targetIndex = max(currentIndex - visibleSize, 0)
        val indexList =
            selectableState.keys.subList(targetIndex, currentIndex)
                .withIndex()
                .filter { it.value.selectable }
                .map { currentIndex - it.index }
                .filter { it >= 0 }
        selectableState.addElementsToSelection(indexList, targetIndex)
    }

    override suspend fun onScrollPageDownAndSelectItem(currentIndex: Int) {
        val firstVisible = selectableState.firstVisibleItemIndex
        val visibleSize = selectableState.layoutInfo.visibleItemsInfo.size
        val targetIndex = min(firstVisible + visibleSize, selectableState.keys.lastIndex)
        if (!selectableState.keys[targetIndex].selectable) {
            selectableState.indexOfNextSelectable(currentIndex) ?: selectableState.indexOfPreviousSelectable(currentIndex)?.let {
                selectableState.selectSingleItem(it)
            }
        } else {
            selectableState.selectSingleItem(targetIndex)
        }
    }

    override suspend fun onScrollPageDownAndExtendSelection(currentIndex: Int) {
        val visibleSize = selectableState.layoutInfo.visibleItemsInfo.size
        val targetIndex = min(currentIndex + visibleSize, selectableState.keys.lastIndex)
        val indexList =
            selectableState.keys.subList(currentIndex, targetIndex)
                .withIndex()
                .filter { it.value.selectable }
                .map { currentIndex + it.index }
                .filter { it <= selectableState.keys.lastIndex }
                .toList()
        selectableState.addElementsToSelection(indexList)
    }

    override suspend fun onEdit(currentIndex: Int) {
        // ij with this shortcut just focus the first element with issue
        // unavailable here
    }
}
