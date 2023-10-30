package org.jetbrains.jewel.foundation.lazy.tree

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.lazy.DefaultSelectableColumnKeybindings
import org.jetbrains.jewel.foundation.lazy.DefaultSelectableOnKeyEvent
import org.jetbrains.jewel.foundation.lazy.SelectableColumnKeybindings
import org.jetbrains.jewel.foundation.lazy.SelectableColumnOnKeyEvent
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListKey
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.utils.Log

interface KeyBindingActions {

    val keybindings: SelectableColumnKeybindings
    val actions: SelectableColumnOnKeyEvent

    fun handleOnKeyEvent(
        event: KeyEvent,
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    ): KeyEvent.() -> Boolean
}

interface PointerEventActions {

    fun handlePointerEventPress(
        pointerEvent: PointerEvent,
        keyBindings: SelectableColumnKeybindings,
        selectableLazyListState: SelectableLazyListState,
        selectionMode: SelectionMode,
        allKeys: List<SelectableLazyListKey>,
        key: Any,
    ) {
        with(keyBindings) {
            when {
                pointerEvent.keyboardModifiers.isKeyboardMultiSelectionKeyPressed && pointerEvent.keyboardModifiers.isCtrlPressed -> {
                    Log.i("ctrl and shift pressed on click")
                    // do nothing
                }

                pointerEvent.keyboardModifiers.isKeyboardMultiSelectionKeyPressed -> {
                    Log.i("shift pressed on click")
                    onExtendSelectionToKey(key, allKeys, selectableLazyListState, selectionMode)
                }

                pointerEvent.keyboardModifiers.isCtrlPressed || pointerEvent.keyboardModifiers.isMetaPressed -> {
                    Log.i("ctrl pressed on click")
                    toggleKeySelection(key, allKeys, selectableLazyListState)
                }

                else -> {
                    Log.i("single click")
                    selectableLazyListState.selectedKeys = listOf(key)
                    selectableLazyListState.lastActiveItemIndex = allKeys.indexOfFirst { it.key == key }
                }
            }
        }
    }

    fun toggleKeySelection(
        key: Any,
        allKeys: List<SelectableLazyListKey>,
        selectableLazyListState: SelectableLazyListState,
    ) {
        if (selectableLazyListState.selectedKeys.contains(key)) {
            selectableLazyListState.selectedKeys =
                selectableLazyListState.selectedKeys.toMutableList().also { it.remove(key) }
        } else {
            selectableLazyListState.selectedKeys =
                selectableLazyListState.selectedKeys.toMutableList().also { it.add(key) }
        }
        selectableLazyListState.lastActiveItemIndex = allKeys.indexOfFirst { it == key }
    }

    fun onExtendSelectionToKey(
        key: Any,
        allKeys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    ) {
        if (selectionMode == SelectionMode.None) return
        if (selectionMode == SelectionMode.Single) {
            state.selectedKeys = listOf(key)
        } else {
            val currentIndex = allKeys.indexOfFirst { it.key == key }.coerceAtLeast(0)
            val lastFocussed = state.lastActiveItemIndex ?: currentIndex
            val indexInterval = if (currentIndex > lastFocussed) {
                lastFocussed..currentIndex
            } else {
                lastFocussed downTo currentIndex
            }
            val keys = buildList {
                for (i in indexInterval) {
                    val currentKey = allKeys[i]
                    if (currentKey is SelectableLazyListKey.Selectable && !state.selectedKeys.contains(allKeys[i].key)) {
                        add(currentKey.key)
                    }
                }
            }
            state.selectedKeys = state.selectedKeys.toMutableList().also { it.addAll(keys) }
            state.lastActiveItemIndex = allKeys.indexOfFirst { it.key == key }
        }
    }
}

class DefaultSelectableLazyColumnEventAction : PointerEventActions

class DefaultTreeViewPointerEventAction(
    private val treeState: TreeState,
) : PointerEventActions {

    override fun handlePointerEventPress(
        pointerEvent: PointerEvent,
        keyBindings: SelectableColumnKeybindings,
        selectableLazyListState: SelectableLazyListState,
        selectionMode: SelectionMode,
        allKeys: List<SelectableLazyListKey>,
        key: Any,
    ) {
        with(keyBindings) {
            when {
                pointerEvent.keyboardModifiers.isKeyboardMultiSelectionKeyPressed && pointerEvent.keyboardModifiers.isCtrlPressed -> {
                    Log.t("ctrl and shift pressed on click")
                }

                pointerEvent.keyboardModifiers.isKeyboardMultiSelectionKeyPressed -> {
                    super.onExtendSelectionToKey(key, allKeys, selectableLazyListState, selectionMode)
                }

                pointerEvent.keyboardModifiers.isCtrlPressed -> {
                    Log.t("control pressed")
                    selectableLazyListState.lastKeyEventUsedMouse = false
                    super.toggleKeySelection(key, allKeys, selectableLazyListState)
                }

                else -> {
                    selectableLazyListState.selectedKeys = listOf(key)
                }
            }
        }
    }

    // todo warning: move this away from here
    // for item click that lose focus and fail to match if a operation is a double-click
    private var elementClickedTmpHolder: Any? = null
    internal fun <T> notifyItemClicked(
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
            Log.d("doubleClicked!")
        } else {
            elementClickedTmpHolder = item.id
            // is a single click
            onElementClick(item)
            scope.launch {
                delay(doubleClickTimeDelayMillis)
                if (elementClickedTmpHolder == item.id) elementClickedTmpHolder = null
            }

            Log.d("singleClicked!")
        }
    }
}

class DefaultTreeViewKeyActions(treeState: TreeState) : DefaultSelectableLazyColumnKeyActions() {

    override val keybindings: TreeViewKeybindings = DefaultTreeViewKeybindings
    override val actions: DefaultTreeViewOnKeyEvent = DefaultTreeViewOnKeyEvent(keybindings, treeState = treeState)

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
                Log.d(keyEvent.key.keyCode.toString())
                if (selectionMode == SelectionMode.None) return@lambda false
                when {
                    selectParent() ?: false -> onSelectParent(keys, state)
                    selectChild() ?: false -> onSelectChild(keys, state)
                    super.handleOnKeyEvent(event, keys, state, selectionMode)
                        .invoke(keyEvent) -> return@lambda true

                    else -> return@lambda false
                }
            }
        }
        return@lambda true
    }
}

open class DefaultSelectableLazyColumnKeyActions : KeyBindingActions {

    override val keybindings: SelectableColumnKeybindings
        get() = DefaultSelectableColumnKeybindings

    override val actions: SelectableColumnOnKeyEvent
        get() = DefaultSelectableOnKeyEvent(keybindings)

    override fun handleOnKeyEvent(
        event: KeyEvent,
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    ): KeyEvent.() -> Boolean =
        lambda@{
            if (type == KeyEventType.KeyUp || selectionMode == SelectionMode.None) return@lambda false
            with(keybindings) {
                with(actions) {
                    execute(keys, state, selectionMode)
                }
            }
        }

    context(SelectableColumnKeybindings, SelectableColumnOnKeyEvent)
    private fun KeyEvent.execute(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    ): Boolean {
        when {
            selectNextItem() ?: false -> {
                onSelectNextItem(keys, state)
            }

            selectPreviousItem() ?: false -> onSelectPreviousItem(keys, state)
            selectFirstItem() ?: false -> onSelectFirstItem(keys, state)
            selectLastItem() ?: false -> onSelectLastItem(keys, state)
            edit() ?: false -> onEdit()
            extendSelectionToFirstItem() ?: false -> {
                if (selectionMode == SelectionMode.Multiple) onExtendSelectionToFirst(keys, state)
            }

            extendSelectionToLastItem() ?: false -> {
                if (selectionMode == SelectionMode.Multiple) onExtendSelectionToLastItem(keys, state)
            }

            extendSelectionWithNextItem() ?: false -> {
                if (selectionMode == SelectionMode.Multiple) onExtendSelectionWithNextItem(keys, state)
            }

            extendSelectionWithPreviousItem() ?: false -> {
                if (selectionMode == SelectionMode.Multiple) onExtendSelectionWithPreviousItem(keys, state)
            }

            scrollPageDownAndExtendSelection() ?: false -> {
                if (selectionMode == SelectionMode.Multiple) onScrollPageDownAndExtendSelection(keys, state)
            }

            scrollPageDownAndSelectItem() ?: false -> {
                if (selectionMode == SelectionMode.Multiple) onScrollPageDownAndSelectItem(keys, state)
            }

            scrollPageUpAndExtendSelection() ?: false -> {
                if (selectionMode == SelectionMode.Multiple) onScrollPageUpAndExtendSelection(keys, state)
            }

            scrollPageUpAndSelectItem() ?: false -> {
                if (selectionMode == SelectionMode.Multiple) onScrollPageUpAndSelectItem(keys, state)
            }

            selectAll() ?: false -> {
                if (selectionMode == SelectionMode.Multiple) onSelectAll(keys, state)
            }

            else -> return false
        }
        return true
    }
}
