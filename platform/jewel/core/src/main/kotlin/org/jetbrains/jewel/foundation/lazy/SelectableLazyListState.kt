package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.max

@Suppress("unused")
val LazyListState.visibleItemsRange
    get() = firstVisibleItemIndex..firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size

val SelectableLazyListState.visibleItemsRange
    get() = firstVisibleItemIndex..firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size

/**
 * State object for a selectable lazy list, which extends [ScrollableState].
 *
 * @param lazyListState The state object for the underlying lazy list.
 */
class SelectableLazyListState(
    val lazyListState: LazyListState,
) : ScrollableState by lazyListState {

    internal var lastKeyEventUsedMouse: Boolean = false

    var selectedKeys by mutableStateOf(emptyList<Any>())
    internal var lastActiveItemIndex: Int? = null

    /**
     *
     * @param itemIndex The index of the item to focus on.
     * @param animateScroll Whether to animate the scroll to the focused item.
     * @param scrollOffset The scroll offset for the focused item.
     * @param skipScroll Whether to skip the scroll to the focused item.
     */
    suspend fun scrollToItem(
        itemIndex: Int,
        animateScroll: Boolean = false,
        scrollOffset: Int = 0,
        skipScroll: Boolean = false,
    ) {
        val visibleRange = visibleItemsRange.drop(2).dropLast(4)
        if (!skipScroll && itemIndex !in visibleRange && visibleRange.isNotEmpty()) {
            when {
                itemIndex < visibleRange.first() -> lazyListState.scrollToItem(
                    max(0, itemIndex - 2),
                    animateScroll,
                    scrollOffset,
                )

                itemIndex > visibleRange.last() -> {
                    lazyListState.scrollToItem(max(itemIndex - (visibleRange.size + 1), 0), animateScroll, 0)
                }
            }
        }
        lastActiveItemIndex = itemIndex
    }

    val layoutInfo
        get() = lazyListState.layoutInfo

    /** The index of the first item that is visible */
    val firstVisibleItemIndex: Int
        get() = lazyListState.firstVisibleItemIndex

    /**
     * The scroll offset of the first visible item. Scrolling forward is
     * positive - i.e., the amount that the item is offset backwards
     */
    @Suppress("unused")
    val firstVisibleItemScrollOffset: Int
        get() = lazyListState.firstVisibleItemScrollOffset

    /**
     * [InteractionSource] that will be used to dispatch drag events when
     * this list is being dragged. If you want to know whether the fling (or
     * animated scroll) is in progress, use [isScrollInProgress].
     */
    val interactionSource: InteractionSource
        get() = lazyListState.interactionSource

    // selection handling
//    fun indexOfNextSelectable(currentIndex: Int): Int? {
//        if (currentIndex + 1 > internalKeys.lastIndex) return null
//        for (i in currentIndex + 1..internalKeys.lastIndex) { // todo iterate with instanceOF
//            if (internalKeys[i] is Key.Selectable) return i
//        }
//        return null
//    }
//
//    fun indexOfPreviousSelectable(currentIndex: Int): Int? {
//        if (currentIndex - 1 < 0) return null
//        for (i in currentIndex - 1 downTo 0) {
//            if (internalKeys[i] is Key.Selectable) return i
//        }
//        return null
//    }
//
//    /**
//     * Selects a single item at the specified index within the lazy list.
//     *
//     * @param itemIndex The index of the item to select.
//     * @param changeFocus Whether to change the focus to the selected item.
//     * @param skipScroll Whether to skip the scroll to the selected item.
//     */
//    suspend fun selectSingleItem(itemIndex: Int, changeFocus: Boolean = true, skipScroll: Boolean = false) {
//        if (changeFocus) scrollToItem(itemIndex, skipScroll = skipScroll)
//        selectedIdsMap.clear()
//        selectedIdsMap[keys[itemIndex]] = itemIndex
//        lastSelectedIndex = itemIndex
//    }
//
//    /**
//     * Selects a single item with the specified key within the lazy list.
//     *
//     * @param key The key of the item to select.
//     * @param changeFocus Whether to change the focus to the selected item.
//     * @param skipScroll Whether to skip the scroll to the selected item.
//     */
//    suspend fun selectSingleKey(key: Any, changeFocus: Boolean = true, skipScroll: Boolean = false) {
//        val index = internalKeys.indexOfFirst { it.key == key }
//        if (index >= 0 && internalKeys[index] is Key.Selectable) selectSingleItem(index, changeFocus, skipScroll = skipScroll)
//            lastSelectedIndex = index
//    }
//
//    suspend fun deselectSingleElement(itemIndex: Int, changeFocus: Boolean = true, skipScroll: Boolean = false) {
//        if (changeFocus) scrollToItem(itemIndex, skipScroll = skipScroll)
//        selectedIdsMap.remove(keys[itemIndex])
//    }
//
//    suspend fun toggleSelection(itemIndex: Int, skipScroll: Boolean = false) {
//        if (selectionMode == SelectionMode.None) return
//        selectedIdsMap[keys[itemIndex]]?.let {
//            deselectSingleElement(itemIndex)
//        } ?: if (!isMultiSelectionAllowed) {
//            selectSingleItem(itemIndex, skipScroll = skipScroll)
//        } else {
//            addElementToSelection(itemIndex, skipScroll = skipScroll)
//        }
//    }
//
//    suspend fun toggleSelectionKey(key: Any, skipScroll: Boolean = false) {
//        if (selectionMode == SelectionMode.None) return
//        val index = internalKeys.indexOfFirst { it.key == key }
//        if (index > 0 && internalKeys[index] is Key.Selectable) toggleSelection(index, skipScroll = skipScroll)
//        lastSelectedIndex = index
//    }
//
//    suspend fun onExtendSelectionToIndex(itemIndex: Int, changeFocus: Boolean = true, skipScroll: Boolean = false) {
//        if (selectionMode == SelectionMode.None) return
//        if (!isMultiSelectionAllowed) {
//            selectSingleItem(itemIndex, skipScroll = skipScroll)
//        } else {
//            val lastFocussed = lastSelectedIndex ?: itemIndex
//            val indexInterval = if (itemIndex > lastFocussed) {
//                lastFocussed..itemIndex
//            } else {
//                lastFocussed downTo itemIndex
//            }
//            addElementsToSelection(indexInterval.toList())
//            if (changeFocus) scrollToItem(itemIndex, skipScroll = skipScroll)
//        }
//    }
//
//    @Suppress("unused")
//    internal fun addKeyToSelectionMap(keyIndex: Int) {
//        if (selectionMode == SelectionMode.None) return
//        if (internalKeys[keyIndex] is Key.Selectable) {
//            selectedIdsMap[keys[keyIndex]] = keyIndex
//        }
//    }
//
//    suspend fun addElementToSelection(itemIndex: Int, changeFocus: Boolean = true, skipScroll: Boolean = false) {
//        if (selectionMode == SelectionMode.None) return
//        if (!isMultiSelectionAllowed) {
//            selectSingleItem(itemIndex, false)
//        } else {
//            selectedIdsMap[keys[itemIndex]] = itemIndex
//        }
//        if (changeFocus) scrollToItem(itemIndex, skipScroll = skipScroll)
//        lastSelectedIndex = itemIndex
//    }
//
//    fun deselectAll() {
//        if (selectionMode == SelectionMode.None) return
//        selectedIdsMap.clear()
//        lastSelectedIndex = null
//    }
//
//    suspend fun addElementsToSelection(itemIndexes: List<Int>, itemToFocus: Int? = itemIndexes.lastOrNull()) {
//        if (selectionMode == SelectionMode.None) return
//        if (!isMultiSelectionAllowed) {
//            itemIndexes.lastOrNull()?.let { selectSingleItem(it) }
//        } else {
//            itemIndexes.forEach {
//                selectedIdsMap[keys[it]] = it
//            }
//            itemToFocus?.let { scrollToItem(it) }
//            lastSelectedIndex = itemIndexes.lastOrNull()
//        }
//    }
//
//    @Suppress("unused")
//    suspend fun removeElementsToSelection(itemIndexes: List<Int>, itemToFocus: Int? = itemIndexes.lastOrNull()) {
//        if (selectionMode == SelectionMode.None) return
//        itemIndexes.forEach {
//            selectedIdsMap.remove(keys[it])
//        }
//        itemToFocus?.let { scrollToItem(it) }
//    }
//
//    @Suppress("Unused")
//    suspend fun toggleElementsToSelection(itemIndexes: List<Int>, itemToFocus: Int? = itemIndexes.lastOrNull()) {
//        if (selectionMode == SelectionMode.None) return
//        if (!isMultiSelectionAllowed) {
//            toggleSelection(itemIndexes.last())
//        } else {
//            itemIndexes.forEach { index ->
//                selectedIdsMap[keys[index]]?.let {
//                    selectedIdsMap.remove(keys[index])
//                } ?: { selectedIdsMap[keys[index]] = index }
//            }
//            itemToFocus?.let { scrollToItem(it) }
//            lastSelectedIndex = itemIndexes.lastOrNull()
//        }
//    }
}

private suspend fun LazyListState.scrollToItem(index: Int, animate: Boolean, scrollOffset: Int = 0) {
    if (animate) {
        animateScrollToItem(index, scrollOffset)
    } else {
        scrollToItem(index, scrollOffset)
    }
}

/**
 * Represents a selectable key used in a selectable lazy list.
 */
sealed class SelectableLazyListKey {

    /**
     * The key associated with the item.
     */
    abstract val key: Any

    /**
     * Represents a selectable item key.
     *
     * @param key The key associated with the item.
     */
    class Selectable(
        override val key: Any,
    ) : SelectableLazyListKey()

    /**
     * Represents a non-selectable item key.
     *
     * @param key The key associated with the item.
     */
    class NotSelectable(
        override val key: Any,
    ) : SelectableLazyListKey()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SelectableLazyListKey

        return key == other.key
    }

    override fun hashCode(): Int = key.hashCode()
}

interface SelectableLazyItemScope : LazyItemScope {

    val isSelected: Boolean
    val isActive: Boolean
}

/**
 * Specifies the selection mode for a selectable lazy list.
 */
@Suppress("unused")
enum class SelectionMode {

    /**
     * No selection is allowed.
     */
    None,

    /**
     * Only a single item can be selected.
     */
    Single,

    /**
     * Multiple items can be selected.
     */
    Multiple,
}

/**
 * Remembers the state of a selectable lazy list.
 *
 * @param firstVisibleItemIndex The index of the first visible item.
 * @param firstVisibleItemScrollOffset The scroll offset of the first visible item.
 * @return The remembered state of the selectable lazy list.
 */
@Composable
fun rememberSelectableLazyListState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0,
) = remember {
    SelectableLazyListState(
        LazyListState(
            firstVisibleItemIndex,
            firstVisibleItemScrollOffset,
        ),
    )
}
