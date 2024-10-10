package org.jetbrains.jewel.foundation.lazy.tree

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.lazy.DefaultMacOsSelectableColumnKeybindings
import org.jetbrains.jewel.foundation.lazy.DefaultSelectableColumnKeybindings
import org.jetbrains.jewel.foundation.lazy.DefaultSelectableOnKeyEvent
import org.jetbrains.jewel.foundation.lazy.SelectableColumnKeybindings
import org.jetbrains.jewel.foundation.lazy.SelectableColumnOnKeyEvent
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListKey
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.skiko.hostOs

public interface KeyActions {
    public val keybindings: SelectableColumnKeybindings
    public val actions: SelectableColumnOnKeyEvent

    public fun handleOnKeyEvent(
        event: KeyEvent,
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    ): KeyEvent.() -> Boolean
}

public interface PointerEventActions {
    public fun handlePointerEventPress(
        pointerEvent: PointerEvent,
        keybindings: SelectableColumnKeybindings,
        selectableLazyListState: SelectableLazyListState,
        selectionMode: SelectionMode,
        allKeys: List<SelectableLazyListKey>,
        key: Any,
    )

    public fun toggleKeySelection(
        key: Any,
        allKeys: List<SelectableLazyListKey>,
        selectableLazyListState: SelectableLazyListState,
        selectionMode: SelectionMode,
    )

    public fun onExtendSelectionToKey(
        key: Any,
        allKeys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    )
}

public open class DefaultSelectableLazyColumnEventAction : PointerEventActions {
    override fun handlePointerEventPress(
        pointerEvent: PointerEvent,
        keybindings: SelectableColumnKeybindings,
        selectableLazyListState: SelectableLazyListState,
        selectionMode: SelectionMode,
        allKeys: List<SelectableLazyListKey>,
        key: Any,
    ) {
        with(keybindings) {
            when {
                pointerEvent.keyboardModifiers.isContiguousSelectionKeyPressed &&
                    pointerEvent.keyboardModifiers.isCtrlPressed -> {
                    // do nothing
                }

                pointerEvent.keyboardModifiers.isContiguousSelectionKeyPressed -> {
                    onExtendSelectionToKey(key, allKeys, selectableLazyListState, selectionMode)
                }

                pointerEvent.keyboardModifiers.isMultiSelectionKeyPressed -> {
                    toggleKeySelection(key, allKeys, selectableLazyListState, selectionMode)
                }

                else -> {
                    selectableLazyListState.selectedKeys = setOf(key)
                    selectableLazyListState.lastActiveItemIndex = allKeys.indexOfFirst { it.key == key }
                }
            }
        }
    }

    override fun toggleKeySelection(
        key: Any,
        allKeys: List<SelectableLazyListKey>,
        selectableLazyListState: SelectableLazyListState,
        selectionMode: SelectionMode,
    ) {
        when (selectionMode) {
            SelectionMode.None -> return
            SelectionMode.Single -> selectableLazyListState.selectedKeys = setOf(key)
            SelectionMode.Multiple -> {
                if (key in selectableLazyListState.selectedKeys) {
                    selectableLazyListState.selectedKeys -= key
                } else {
                    selectableLazyListState.selectedKeys += key
                }
            }
        }
        selectableLazyListState.lastActiveItemIndex = allKeys.indexOfFirst { it == key }
    }

    override fun onExtendSelectionToKey(
        key: Any,
        allKeys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    ) {
        if (selectionMode == SelectionMode.None) return
        if (selectionMode == SelectionMode.Single) {
            state.selectedKeys = setOf(key)
        } else {
            val currentIndex = allKeys.indexOfFirst { it.key == key }.coerceAtLeast(0)
            val lastFocussed = state.lastActiveItemIndex ?: currentIndex
            val indexInterval =
                if (currentIndex > lastFocussed) {
                    lastFocussed..currentIndex
                } else {
                    lastFocussed downTo currentIndex
                }
            val keys = buildList {
                for (i in indexInterval) {
                    val currentKey = allKeys[i]
                    if (
                        currentKey is SelectableLazyListKey.Selectable && !state.selectedKeys.contains(allKeys[i].key)
                    ) {
                        add(currentKey.key)
                    }
                }
            }
            state.selectedKeys += keys
            state.lastActiveItemIndex = allKeys.indexOfFirst { it.key == key }
        }
    }
}

public open class DefaultTreeViewPointerEventAction(private val treeState: TreeState) :
    DefaultSelectableLazyColumnEventAction() {
    override fun handlePointerEventPress(
        pointerEvent: PointerEvent,
        keybindings: SelectableColumnKeybindings,
        selectableLazyListState: SelectableLazyListState,
        selectionMode: SelectionMode,
        allKeys: List<SelectableLazyListKey>,
        key: Any,
    ) {
        with(keybindings) {
            when {
                pointerEvent.keyboardModifiers.isContiguousSelectionKeyPressed &&
                    pointerEvent.keyboardModifiers.isCtrlPressed -> {}

                pointerEvent.keyboardModifiers.isContiguousSelectionKeyPressed -> {
                    super.onExtendSelectionToKey(key, allKeys, selectableLazyListState, selectionMode)
                }

                pointerEvent.keyboardModifiers.isMultiSelectionKeyPressed -> {
                    selectableLazyListState.lastKeyEventUsedMouse = false
                    super.toggleKeySelection(key, allKeys, selectableLazyListState, selectionMode)
                }

                else -> {
                    selectableLazyListState.selectedKeys = setOf(key)
                }
            }
        }
    }

    // todo warning: move this away from here
    // for item click that lose focus and fail to match if a operation is a double-click
    private var elementClickedTmpHolder: Any? = null

    @InternalJewelApi
    public fun <T> notifyItemClicked(
        item: Tree.Element<T>,
        scope: CoroutineScope,
        doubleClickTimeDelayMillis: Long,
        onElementClick: (Tree.Element<T>) -> Unit,
        onElementDoubleClick: (Tree.Element<T>) -> Unit,
    ) {
        if (elementClickedTmpHolder == item.id) {
            // is a double click
            if (item is Tree.Element.Node) {
                treeState.toggleNode(item.id)
            }
            onElementDoubleClick(item)
            elementClickedTmpHolder = null
        } else {
            elementClickedTmpHolder = item.id
            // is a single click
            onElementClick(item)
            scope.launch {
                delay(doubleClickTimeDelayMillis)
                if (elementClickedTmpHolder == item.id) elementClickedTmpHolder = null
            }
        }
    }
}

public fun DefaultTreeViewKeyActions(treeState: TreeState): DefaultTreeViewKeyActions {
    val keybindings =
        when {
            hostOs.isMacOS -> DefaultMacOsTreeColumnKeybindings
            else -> DefaultTreeViewKeybindings
        }
    return DefaultTreeViewKeyActions(keybindings, DefaultTreeViewOnKeyEvent(keybindings, treeState))
}

public class DefaultTreeViewKeyActions(
    override val keybindings: TreeViewKeybindings,
    override val actions: DefaultTreeViewOnKeyEvent,
) : DefaultSelectableLazyColumnKeyActions(keybindings, actions) {
    override fun handleOnKeyEvent(
        event: KeyEvent,
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    ): KeyEvent.() -> Boolean = lambda@{
        if (type == KeyEventType.KeyUp) return@lambda false
        val keyEvent = this
        with(keybindings) {
            with(actions) {
                if (selectionMode == SelectionMode.None) return@lambda false
                when {
                    isSelectParent -> onSelectParent(keys, state)
                    isSelectChild -> onSelectChild(keys, state)
                    super.handleOnKeyEvent(event, keys, state, selectionMode).invoke(keyEvent) -> return@lambda true

                    else -> return@lambda false
                }
            }
        }
        return@lambda true
    }
}

public open class DefaultSelectableLazyColumnKeyActions(
    override val keybindings: SelectableColumnKeybindings,
    override val actions: SelectableColumnOnKeyEvent = DefaultSelectableOnKeyEvent(keybindings),
) : KeyActions {
    public companion object :
        DefaultSelectableLazyColumnKeyActions(
            when {
                hostOs.isMacOS -> DefaultMacOsSelectableColumnKeybindings
                else -> DefaultSelectableColumnKeybindings
            }
        )

    override fun handleOnKeyEvent(
        event: KeyEvent,
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    ): KeyEvent.() -> Boolean = lambda@{
        if (type == KeyEventType.KeyUp || selectionMode == SelectionMode.None) return@lambda false
        with(keybindings) { with(actions) { execute(keys, state, selectionMode) } }
    }

    context(SelectableColumnKeybindings, SelectableColumnOnKeyEvent)
    private fun KeyEvent.execute(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    ): Boolean {
        val singleSelectionEventHandled = handleSingleSelectionEvents(keys, state)
        if (singleSelectionEventHandled) {
            return true
        }
        if (selectionMode == SelectionMode.Multiple) {
            val multipleSelectionEventHandled = handleMultipleSelectionEvents(keys, state)
            if (multipleSelectionEventHandled) {
                return true
            }
        }
        return false
    }

    context(SelectableColumnKeybindings, SelectableColumnOnKeyEvent)
    private fun KeyEvent.handleSingleSelectionEvents(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ): Boolean {
        when {
            isSelectNextItem -> onSelectNextItem(keys, state)
            isSelectPreviousItem -> onSelectPreviousItem(keys, state)
            isSelectFirstItem -> onSelectFirstItem(keys, state)
            isSelectLastItem -> onSelectLastItem(keys, state)
            isEdit -> onEdit()
            else -> return false
        }
        return true
    }

    context(SelectableColumnKeybindings, SelectableColumnOnKeyEvent)
    private fun KeyEvent.handleMultipleSelectionEvents(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
    ): Boolean {
        when {
            isExtendSelectionToFirstItem -> onExtendSelectionToFirst(keys, state)
            isExtendSelectionToLastItem -> onExtendSelectionToLastItem(keys, state)
            isExtendSelectionWithNextItem -> onExtendSelectionWithNextItem(keys, state)
            isExtendSelectionWithPreviousItem -> onExtendSelectionWithPreviousItem(keys, state)
            isScrollPageDownAndExtendSelection -> onScrollPageDownAndExtendSelection(keys, state)
            isScrollPageDownAndSelectItem -> onScrollPageDownAndSelectItem(keys, state)
            isScrollPageUpAndExtendSelection -> onScrollPageUpAndExtendSelection(keys, state)
            isScrollPageUpAndSelectItem -> onScrollPageUpAndSelectItem(keys, state)
            isSelectAll -> onSelectAll(keys, state)
            else -> return false
        }
        return true
    }
}
