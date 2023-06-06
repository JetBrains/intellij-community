package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import org.jetbrains.jewel.foundation.tree.KeyBindingScopedActions
import kotlin.math.max
import kotlin.properties.Delegates

@Suppress("unused")
val LazyListState.visibleItemsRange
    get() = firstVisibleItemIndex..firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size

val SelectableLazyListState.visibleItemsRange
    get() = firstVisibleItemIndex..firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size

/**
 * State object for a selectable lazy list, which extends [ScrollableState].
 *
 * @param lazyListState The state object for the underlying lazy list.
 * @param selectionMode The selection mode for the list.
 */
class SelectableLazyListState(
    val lazyListState: LazyListState,
    val selectionMode: SelectionMode = SelectionMode.None
) : ScrollableState by lazyListState {

    private val isMultiSelectionAllowed = selectionMode == SelectionMode.Multiple

    internal var lastKeyEventUsedMouse: Boolean = false
    internal val selectedIdsMap = mutableStateMapOf<SelectableKey, Int>()

    internal val keys: List<SelectableKey>
        get() = internalKeys
    private var internalKeys = mutableListOf<SelectableKey>()
    val selectedItemIndexes get() = selectedIdsMap.values.toList()
    var lastSelectedIndex by mutableStateOf<Int?>(null)
    private var uuid: String? = null

    internal fun checkUUID(uuid: String) {
        if (this.uuid == null) {
            this.uuid = uuid
        } else {
            require(this.uuid == this.uuid) {
                "Do not attach the same ${this::class.simpleName} to different SelectableLazyColumns."
            }
        }
    }

    internal fun attachKeys(keys: List<SelectableKey>) {
        internalKeys.removeAll(keys)
        internalKeys.addAll(keys)
        updateKeysIndexes()
    }

    internal fun attachKey(key: SelectableKey) {
        internalKeys.remove(key)
        internalKeys.add(key)
        updateKeysIndexes()
    }

    internal fun clearKeys() = internalKeys.clear()

    internal fun updateKeysIndexes() {
        keys.forEachIndexed { index, key ->
            selectedIdsMap.computeIfPresent(key) { _, _ -> index }
        }
    }

    var keybindings: SelectableColumnKeybindings by Delegates.notNull()
    internal fun attachKeybindings(keybindings: KeyBindingScopedActions) {
        this.keybindings = keybindings.keybindings
    }

    /**
     * Focuses on the item at the specified index within the lazy list.
     *
     * @param itemIndex The index of the item to focus on.
     * @param animateScroll Whether to animate the scroll to the focused item.
     * @param scrollOffset The scroll offset for the focused item.
     * @param skipScroll Whether to skip the scroll to the focused item.
     */
    suspend fun focusItem(itemIndex: Int, animateScroll: Boolean = false, scrollOffset: Int = 0, skipScroll: Boolean = false) {
        val visibleRange = visibleItemsRange.drop(2).dropLast(4)
        if (!skipScroll && itemIndex !in visibleRange && visibleRange.isNotEmpty()) {
            when {
                itemIndex < visibleRange.first() -> lazyListState.scrollToItem(max(0, itemIndex - 2), animateScroll, scrollOffset)
                itemIndex > visibleRange.last() -> {
                    lazyListState.scrollToItem(max(itemIndex - (visibleRange.size + 1), 0), animateScroll, 0)
                }
            }
        }
        lastFocusedIndexState.value = itemIndex
        focusVisibleItem(itemIndex)
    }

    private fun focusVisibleItem(itemIndex: Int) {
        layoutInfo.visibleItemsInfo
            .find { it.index == itemIndex }
            ?.key
            ?.let { it as? SelectableKey.Focusable }
            ?.focusRequester
            ?.runCatching { requestFocus() } // another recomposition may be launched, so we are not interested to focus item while we are recomposing
    }

    internal sealed interface LastFocusedKeyContainer {
        object NotSet : LastFocusedKeyContainer

        @JvmInline
        value class Set(val key: Any?) : LastFocusedKeyContainer
    }

    internal val lastFocusedKeyState: MutableState<LastFocusedKeyContainer> = mutableStateOf(LastFocusedKeyContainer.NotSet)
    internal val lastFocusedIndexState: MutableState<Int?> = mutableStateOf(null)

    val lastFocusedIndex
        get() = lastFocusedIndexState.value

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
    fun indexOfNextSelectable(currentIndex: Int): Int? {
        if (currentIndex + 1 > keys.lastIndex) return null
        for (i in currentIndex + 1..keys.lastIndex) {
            if (keys[i].selectable) return i
        }
        return null
    }

    fun indexOfPreviousSelectable(currentIndex: Int): Int? {
        if (currentIndex - 1 < 0) return null
        for (i in currentIndex - 1 downTo 0) {
            if (keys[i].selectable) return i
        }
        return null
    }

    /**
     * Selects a single item at the specified index within the lazy list.
     *
     * @param itemIndex The index of the item to select.
     * @param changeFocus Whether to change the focus to the selected item.
     * @param skipScroll Whether to skip the scroll to the selected item.
     */
    suspend fun selectSingleItem(itemIndex: Int, changeFocus: Boolean = true, skipScroll: Boolean = false) {
        if (changeFocus) focusItem(itemIndex, skipScroll = skipScroll)
        selectedIdsMap.clear()
        selectedIdsMap[keys[itemIndex]] = itemIndex
        lastSelectedIndex = itemIndex
    }

    /**
     * Selects a single item with the specified key within the lazy list.
     *
     * @param key The key of the item to select.
     * @param changeFocus Whether to change the focus to the selected item.
     * @param skipScroll Whether to skip the scroll to the selected item.
     */
    suspend fun selectSingleKey(key: Any, changeFocus: Boolean = true, skipScroll: Boolean = false) {
        val index = keys.indexOfFirst { it.key == key }
        if (index >= 0 && keys[index].selectable) selectSingleItem(index, changeFocus, skipScroll = skipScroll)
        lastSelectedIndex = index
    }

    suspend fun deselectSingleElement(itemIndex: Int, changeFocus: Boolean = true, skipScroll: Boolean = false) {
        if (changeFocus) focusItem(itemIndex, skipScroll = skipScroll)
        selectedIdsMap.remove(keys[itemIndex])
    }

    suspend fun toggleSelection(itemIndex: Int, skipScroll: Boolean = false) {
        if (selectionMode == SelectionMode.None) return
        selectedIdsMap[keys[itemIndex]]?.let {
            deselectSingleElement(itemIndex)
        } ?: if (!isMultiSelectionAllowed) {
            selectSingleItem(itemIndex, skipScroll = skipScroll)
        } else {
            addElementToSelection(itemIndex, skipScroll = skipScroll)
        }
    }

    suspend fun toggleSelectionKey(key: Any, skipScroll: Boolean = false) {
        if (selectionMode == SelectionMode.None) return
        val index = keys.indexOfFirst { it.key == key }
        if (index > 0 && keys[index].selectable) toggleSelection(index, skipScroll = skipScroll)
        lastSelectedIndex = index
    }

    suspend fun onExtendSelectionToIndex(itemIndex: Int, changeFocus: Boolean = true, skipScroll: Boolean = false) {
        if (selectionMode == SelectionMode.None) return
        if (!isMultiSelectionAllowed) {
            selectSingleItem(itemIndex, skipScroll = skipScroll)
        } else {
            val lastFocussed = lastSelectedIndex ?: itemIndex
            val indexInterval = if (itemIndex > lastFocussed) {
                lastFocussed..itemIndex
            } else {
                lastFocussed downTo itemIndex
            }
            addElementsToSelection(indexInterval.toList())
            if (changeFocus) focusItem(itemIndex, skipScroll = skipScroll)
        }
    }

    @Suppress("unused")
    internal fun addKeyToSelectionMap(keyIndex: Int) {
        if (selectionMode == SelectionMode.None) return
        if (keys[keyIndex].selectable) {
            selectedIdsMap[keys[keyIndex]] = keyIndex
        }
    }

    suspend fun addElementToSelection(itemIndex: Int, changeFocus: Boolean = true, skipScroll: Boolean = false) {
        if (selectionMode == SelectionMode.None) return
        if (!isMultiSelectionAllowed) {
            selectSingleItem(itemIndex, false)
        } else {
            selectedIdsMap[keys[itemIndex]] = itemIndex
        }
        if (changeFocus) focusItem(itemIndex, skipScroll = skipScroll)
        lastSelectedIndex = itemIndex
    }

    fun deselectAll() {
        if (selectionMode == SelectionMode.None) return
        selectedIdsMap.clear()
        lastSelectedIndex = null
    }

    suspend fun addElementsToSelection(itemIndexes: List<Int>, itemToFocus: Int? = itemIndexes.lastOrNull()) {
        if (selectionMode == SelectionMode.None) return
        if (!isMultiSelectionAllowed) {
            itemIndexes.lastOrNull()?.let { selectSingleItem(it) }
        } else {
            itemIndexes.forEach {
                selectedIdsMap[keys[it]] = it
            }
            itemToFocus?.let { focusItem(it) }
            lastSelectedIndex = itemIndexes.lastOrNull()
        }
    }

    @Suppress("unused")
    suspend fun removeElementsToSelection(itemIndexes: List<Int>, itemToFocus: Int? = itemIndexes.lastOrNull()) {
        if (selectionMode == SelectionMode.None) return
        itemIndexes.forEach {
            selectedIdsMap.remove(keys[it])
        }
        itemToFocus?.let { focusItem(it) }
    }

    @Suppress("Unused")
    suspend fun toggleElementsToSelection(itemIndexes: List<Int>, itemToFocus: Int? = itemIndexes.lastOrNull()) {
        if (selectionMode == SelectionMode.None) return
        if (!isMultiSelectionAllowed) {
            toggleSelection(itemIndexes.last())
        } else {
            itemIndexes.forEach { index ->
                selectedIdsMap[keys[index]]?.let {
                    selectedIdsMap.remove(keys[index])
                } ?: { selectedIdsMap[keys[index]] = index }
            }
            itemToFocus?.let { focusItem(it) }
            lastSelectedIndex = itemIndexes.lastOrNull()
        }
    }
}

private suspend fun LazyListState.scrollToItem(index: Int, animate: Boolean, scrollOffset: Int = 0) =
    if (animate) animateScrollToItem(index, scrollOffset) else scrollToItem(index, scrollOffset)

/**
 * Represents a selectable key used in a selectable lazy list.
 */
internal sealed class SelectableKey {

    /**
     * The key associated with the item.
     */
    abstract val key: Any

    /**
     * Determines if the item is selectable.
     */
    abstract val selectable: Boolean

    /**
     * Represents a focusable item key.
     *
     * @param focusRequester The focus requester for the item.
     * @param key The key associated with the item.
     * @param selectable Whether the item is selectable.
     */
    internal class Focusable(
        internal val focusRequester: FocusRequester,
        override val key: Any,
        override val selectable: Boolean
    ) : SelectableKey()

    /**
     * Represents a non-focusable item key.
     *
     * @param key The key associated with the item.
     * @param selectable Whether the item is selectable.
     */
    internal class NotFocusable(
        override val key: Any,
        override val selectable: Boolean
    ) : SelectableKey()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SelectableKey

        return key == other.key
    }

    override fun hashCode(): Int = key.hashCode()
}

interface SelectableLazyItemScope : LazyItemScope {

    val isSelected: Boolean
    val isFocused: Boolean
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
    Multiple
}

/**
 * Remembers the state of a selectable lazy list.
 *
 * @param firstVisibleItemIndex The index of the first visible item.
 * @param firstVisibleItemScrollOffset The scroll offset of the first visible item.
 * @param selectionMode The selection mode for the list.
 * @return The remembered state of the selectable lazy list.
 */
@Composable
fun rememberSelectableLazyListState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0,
    selectionMode: SelectionMode = SelectionMode.Multiple
) = remember { SelectableLazyListState(LazyListState(firstVisibleItemIndex, firstVisibleItemScrollOffset), selectionMode) }
