package org.jetbrains.jewel.foundation.lazy

import kotlin.math.max
import kotlin.math.min
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListKey.Selectable

public interface SelectableColumnOnKeyEvent {
    public val keybindings: SelectableColumnKeybindings

    /** Select First Node. */
    public fun onSelectFirstItem(allKeys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        for (index in allKeys.indices) {
            val key = allKeys[index]
            if (key is Selectable) {
                state.selectedKeys = setOf(key.key)
                state.lastActiveItemIndex = index
                return
            }
        }
    }

    /** Extend Selection to First Node inherited from Move Caret to Text Start with Selection. */
    public fun onExtendSelectionToFirst(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        val initialIndex = state.lastActiveItemIndex ?: return
        val newSelection = HashSet<Any>(max(initialIndex, state.selectedKeys.size)).apply { addAll(state.selectedKeys) }
        var lastActiveItemIndex = initialIndex
        for (index in initialIndex - 1 downTo 0) {
            val key = keys[index]
            if (key is Selectable) {
                newSelection.add(key.key)
                lastActiveItemIndex = index
            }
        }
        state.lastActiveItemIndex = lastActiveItemIndex
        state.selectedKeys = newSelection
    }

    /** Select Last Node inherited from Move Caret to Text End. */
    public fun onSelectLastItem(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        for (index in keys.lastIndex downTo 0) {
            val key = keys[index]
            if (key is Selectable) {
                state.selectedKeys = setOf(key.key)
                state.lastActiveItemIndex = index
                return
            }
        }
    }

    /** Extend Selection to Last Node inherited from Move Caret to Text End with Selection. */
    public fun onExtendSelectionToLastItem(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        val initialIndex = state.lastActiveItemIndex ?: return
        val newSelection =
            HashSet<Any>(max(keys.size - initialIndex, state.selectedKeys.size)).apply { addAll(state.selectedKeys) }
        var lastActiveItemIndex = initialIndex
        for (index in initialIndex + 1..keys.lastIndex) {
            val key = keys[index]
            if (key is Selectable) {
                newSelection.add(key.key)
                lastActiveItemIndex = index
            }
        }
        state.lastActiveItemIndex = lastActiveItemIndex
        state.selectedKeys = newSelection
    }

    /** Select Previous Node inherited from Up. */
    public fun onSelectPreviousItem(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        val initialIndex = state.lastActiveItemIndex ?: return
        for (index in initialIndex - 1 downTo 0) {
            val key = keys[index]
            if (key is Selectable) {
                state.selectedKeys = setOf(key.key)
                state.lastActiveItemIndex = index
                return
            }
        }
    }

    /** Extend Selection with Previous Node inherited from Up with Selection. */
    public fun onExtendSelectionWithPreviousItem(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        // todo we need deselect if we are changing direction
        val initialIndex = state.lastActiveItemIndex ?: return
        for (index in initialIndex - 1 downTo 0) {
            val key = keys[index]
            if (key is Selectable) {
                state.selectedKeys += key.key
                state.lastActiveItemIndex = index
                return
            }
        }
    }

    /** Select Next Node inherited from Down. */
    public fun onSelectNextItem(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        val initialIndex = state.lastActiveItemIndex ?: return
        for (index in initialIndex + 1..keys.lastIndex) {
            val key = keys[index]
            if (key is Selectable) {
                state.selectedKeys = setOf(key.key)
                state.lastActiveItemIndex = index
                return
            }
        }
    }

    /** Extend Selection with Next Node inherited from Down with Selection. */
    public fun onExtendSelectionWithNextItem(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        // todo we need deselect if we are changing direction
        val initialIndex = state.lastActiveItemIndex ?: return
        for (index in initialIndex + 1..keys.lastIndex) {
            val key = keys[index]
            if (key is Selectable) {
                state.selectedKeys += key.key
                state.lastActiveItemIndex = index
                return
            }
        }
    }

    /** Scroll Page Up and Select Node inherited from Page Up. */
    public fun onScrollPageUpAndSelectItem(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        val visibleSize = state.layoutInfo.visibleItemsInfo.size
        val targetIndex = max((state.lastActiveItemIndex ?: 0) - visibleSize, 0)
        state.selectedKeys = setOf(keys[targetIndex].key)
        state.lastActiveItemIndex = targetIndex
    }

    /** Scroll Page Up and Extend Selection inherited from Page Up with Selection. */
    public fun onScrollPageUpAndExtendSelection(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        val visibleSize = state.layoutInfo.visibleItemsInfo.size
        val targetIndex = max((state.lastActiveItemIndex ?: 0) - visibleSize, 0)
        val newSelectionList =
            keys
                .subList(targetIndex, (state.lastActiveItemIndex ?: 0))
                .withIndex()
                .filter { it.value is Selectable }
                .let { state.selectedKeys + it.map { selectableKey -> selectableKey.value.key } }
        state.selectedKeys = newSelectionList
        state.lastActiveItemIndex = targetIndex
    }

    /** Scroll Page Down and Select Node inherited from Page Down. */
    public fun onScrollPageDownAndSelectItem(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        val visibleSize = state.layoutInfo.visibleItemsInfo.size
        val targetIndex = min((state.lastActiveItemIndex ?: 0) + visibleSize, keys.lastIndex)
        state.selectedKeys = setOf(keys[targetIndex].key)
        state.lastActiveItemIndex = targetIndex
    }

    /** Scroll Page Down and Extend Selection inherited from Page Down with Selection. */
    public fun onScrollPageDownAndExtendSelection(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        val visibleSize = state.layoutInfo.visibleItemsInfo.size
        val targetIndex = min((state.lastActiveItemIndex ?: 0) + visibleSize, keys.lastIndex)
        val newSelectionList =
            keys.subList(state.lastActiveItemIndex ?: 0, targetIndex).filterIsInstance<Selectable>().let {
                state.selectedKeys + it.map { selectableKey -> selectableKey.key }
            }
        state.selectedKeys = newSelectionList
        state.lastActiveItemIndex = targetIndex
    }

    /** Edit Item. */
    public fun onEdit() {
        // IntelliJ focuses the first element with an issue when this is pressed.
        // It is thus unavailable here.
    }

    /** Select All. */
    public fun onSelectAll(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        state.selectedKeys = keys.filterIsInstance<Selectable>().map { it.key }.toSet()
    }
}

public open class DefaultSelectableOnKeyEvent(override val keybindings: SelectableColumnKeybindings) :
    SelectableColumnOnKeyEvent {
    public companion object : DefaultSelectableOnKeyEvent(DefaultSelectableColumnKeybindings)
}
