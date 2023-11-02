package org.jetbrains.jewel.foundation.lazy

import org.jetbrains.jewel.foundation.lazy.SelectableLazyListKey.Selectable
import kotlin.math.max
import kotlin.math.min

public interface SelectableColumnOnKeyEvent {

    public val keybindings: SelectableColumnKeybindings

    /**
     * Select First Node.
     */
    public fun onSelectFirstItem(
        allKeys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ) {
        val firstSelectable = allKeys.withIndex().firstOrNull { it.value is Selectable }
        if (firstSelectable != null) {
            state.selectedKeys = listOf(firstSelectable.value.key)
            state.lastActiveItemIndex = firstSelectable.index
        }
    }

    /**
     * Extend Selection to First Node inherited from Move Caret to Text Start
     * with Selection.
     */
    public fun onExtendSelectionToFirst(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ) {
        state.lastActiveItemIndex?.let {
            val iterator = keys.listIterator(it)
            val list = buildList {
                while (iterator.hasPrevious()) {
                    val previous = iterator.previous()
                    if (previous is Selectable) {
                        add(previous.key)
                        state.lastActiveItemIndex = (iterator.previousIndex() + 1).coerceAtMost(keys.size)
                    }
                }
            }
            if (list.isNotEmpty()) {
                state.selectedKeys =
                    state.selectedKeys.toMutableList()
                        .also { selectionList -> selectionList.addAll(list) }
            }
        }
    }

    /**
     * Select Last Node inherited from Move Caret to Text End.
     */
    public fun onSelectLastItem(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ) {
        keys.withIndex()
            .lastOrNull { it.value is Selectable }
            ?.let {
                state.selectedKeys = listOf(it)
                state.lastActiveItemIndex = it.index
            }
    }

    /**
     * Extend Selection to Last Node inherited from Move Caret to Text End with
     * Selection.
     */
    public fun onExtendSelectionToLastItem(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ) {
        state.lastActiveItemIndex?.let {
            val list = mutableListOf<Any>(state.selectedKeys)
            keys.subList(it, keys.lastIndex).forEachIndexed { index, selectableLazyListKey ->
                if (selectableLazyListKey is Selectable) {
                    list.add(selectableLazyListKey.key)
                }
                state.lastActiveItemIndex = index
            }
            state.selectedKeys = list
        }
    }

    /**
     * Select Previous Node inherited from Up.
     */
    public fun onSelectPreviousItem(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ) {
        state.lastActiveItemIndex?.let { lastActiveIndex ->
            if (lastActiveIndex == 0) return@let
            keys.withIndex()
                .toList()
                .dropLastWhile { it.index >= lastActiveIndex }
                .reversed()
                .firstOrNull { it.value is Selectable }
                ?.let { (index, selectableKey) ->
                    state.selectedKeys = listOf(selectableKey.key)
                    state.lastActiveItemIndex = index
                }
        }
    }

    /**
     * Extend Selection with Previous Node inherited from Up with Selection.
     */
    public fun onExtendSelectionWithPreviousItem(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ) {
        state.lastActiveItemIndex?.let { lastActiveIndex ->
            if (lastActiveIndex == 0) return@let
            keys.withIndex()
                .toList()
                .dropLastWhile { it.index >= lastActiveIndex }
                .reversed()
                .firstOrNull { it.value is Selectable }
                ?.let { (index, selectableKey) ->
                    state.selectedKeys = state.selectedKeys + selectableKey.key
                    state.lastActiveItemIndex = index
                }
        }
    }

    /**
     * Select Next Node inherited from Down.
     */
    public fun onSelectNextItem(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ) {
        state.lastActiveItemIndex?.let { lastActiveIndex ->
            if (lastActiveIndex == keys.lastIndex) return@let
            keys.withIndex()
                .dropWhile { it.index <= lastActiveIndex }
                .firstOrNull { it.value is Selectable }
                ?.let { (index, selectableKey) ->
                    state.selectedKeys = listOf(selectableKey.key)
                    state.lastActiveItemIndex = index
                }
        }
    }

    /**
     * Extend Selection with Next Node inherited from Down with Selection.
     */
    public fun onExtendSelectionWithNextItem(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ) {
        // todo we need deselect if we are changing direction
        state.lastActiveItemIndex?.let { lastActiveIndex ->
            if (lastActiveIndex == keys.lastIndex) return@let
            keys
                .withIndex()
                .dropWhile { it.index <= lastActiveIndex }
                .firstOrNull { it.value is Selectable }
                ?.let { (index, selectableKey) ->
                    state.selectedKeys = state.selectedKeys + selectableKey.key
                    state.lastActiveItemIndex = index
                }
        }
    }

    /**
     * Scroll Page Up and Select Node inherited from Page Up.
     */
    public fun onScrollPageUpAndSelectItem(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ) {
        val visibleSize = state.layoutInfo.visibleItemsInfo.size
        val targetIndex = max((state.lastActiveItemIndex ?: 0) - visibleSize, 0)
        state.selectedKeys = listOf(keys[targetIndex].key)
        state.lastActiveItemIndex = targetIndex
    }

    /**
     * Scroll Page Up and Extend Selection inherited from Page Up with
     * Selection.
     */
    public fun onScrollPageUpAndExtendSelection(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ) {
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

    /**
     * Scroll Page Down and Select Node inherited from Page Down.
     */
    public fun onScrollPageDownAndSelectItem(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ) {
        val visibleSize = state.layoutInfo.visibleItemsInfo.size
        val targetIndex = min((state.lastActiveItemIndex ?: 0) + visibleSize, keys.lastIndex)
        state.selectedKeys = listOf(keys[targetIndex].key)
        state.lastActiveItemIndex = targetIndex
    }

    /**
     * Scroll Page Down and Extend Selection inherited from Page Down with
     * Selection.
     */
    public fun onScrollPageDownAndExtendSelection(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ) {
        val visibleSize = state.layoutInfo.visibleItemsInfo.size
        val targetIndex = min((state.lastActiveItemIndex ?: 0) + visibleSize, keys.lastIndex)
        val newSelectionList =
            keys.subList(state.lastActiveItemIndex ?: 0, targetIndex)
                .filterIsInstance<Selectable>()
                .let { state.selectedKeys + it.map { selectableKey -> selectableKey.key } }
        state.selectedKeys = newSelectionList
        state.lastActiveItemIndex = targetIndex
    }

    /**
     * Edit Item.
     */
    public fun onEdit() {
        // IntelliJ focuses the first element with an issue when this is pressed.
        // It is thus unavailable here.
    }

    /**
     * Select All.
     */
    public fun onSelectAll(keys: List<SelectableLazyListKey>, state: SelectableLazyListState) {
        state.selectedKeys = keys.filterIsInstance<Selectable>().map { it.key }
    }
}

public open class DefaultSelectableOnKeyEvent(
    override val keybindings: SelectableColumnKeybindings,
) : SelectableColumnOnKeyEvent {

    public companion object : DefaultSelectableOnKeyEvent(DefaultSelectableColumnKeybindings)
}
