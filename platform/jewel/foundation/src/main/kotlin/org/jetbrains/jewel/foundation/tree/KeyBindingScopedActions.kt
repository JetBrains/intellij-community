package org.jetbrains.jewel.foundation.tree

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.lazy.DefaultSelectableColumnKeybindings
import org.jetbrains.jewel.foundation.lazy.DefaultSelectableOnKeyEvent
import org.jetbrains.jewel.foundation.lazy.SelectableColumnKeybindings
import org.jetbrains.jewel.foundation.lazy.SelectableColumnOnKeyEvent
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.utils.Log

interface KeyBindingScopedActions {

    val keybindings: SelectableColumnKeybindings
    val actions: SelectableColumnOnKeyEvent

    fun handleOnKeyEvent(coroutineScope: CoroutineScope): KeyEvent.(Int) -> Boolean
}

interface PointerEventScopedActions {

    fun handlePointerEventPress(
        pointerEvent: PointerEvent,
        keyBindings: SelectableColumnKeybindings,
        scope: CoroutineScope,
        key: Any
    )
}

class DefaultSelectableLazyColumnPointerEventAction(private val state: SelectableLazyListState) : PointerEventScopedActions {

    override fun handlePointerEventPress(
        pointerEvent: PointerEvent,
        keyBindings: SelectableColumnKeybindings,
        scope: CoroutineScope,
        key: Any
    ) {
        with(keyBindings) {
            when {
                pointerEvent.keyboardModifiers.isKeyboardMultiSelectionKeyPressed && pointerEvent.keyboardModifiers.isCtrlPressed -> {
                    Log.i("ctrl and shift pressed on click")
                    // do nothing
                }

                pointerEvent.keyboardModifiers.isKeyboardMultiSelectionKeyPressed -> {
                    Log.i("shift pressed on click")
                    scope.launch {
                        state.onExtendSelectionToIndex(state.keys.indexOfFirst { it.key == key }, skipScroll = true)
                    }
                }

                pointerEvent.keyboardModifiers.isCtrlPressed -> {
                    Log.i("ctrl pressed on click")
                    state.lastKeyEventUsedMouse = false
                    scope.launch {
                        state.toggleSelectionKey(key, skipScroll = true)
                    }
                }

                else -> {
                    Log.i("single click")
                    scope.launch {
                        state.selectSingleKey(key, skipScroll = true)
                    }
                }
            }
        }
    }
}

class DefaultTreeViewPointerEventAction<T>(
    private val treeState: TreeState,
    private val platformDoubleClickDelay: Long,
    private val onElementClick: (Tree.Element<T>) -> Unit,
    private val onElementDoubleClick: (Tree.Element<T>) -> Unit
) : PointerEventScopedActions {

    override fun handlePointerEventPress(
        pointerEvent: PointerEvent,
        keyBindings: SelectableColumnKeybindings,
        scope: CoroutineScope,
        key: Any
    ) {
        with(keyBindings) {
            when {
                pointerEvent.keyboardModifiers.isKeyboardMultiSelectionKeyPressed && pointerEvent.keyboardModifiers.isCtrlPressed -> {
                    Log.t("ctrl and shift pressed on click")
                }

                pointerEvent.keyboardModifiers.isKeyboardMultiSelectionKeyPressed -> {
                    Log.t("ShiftClicked ")
                    scope.launch {
                        treeState.delegate.onExtendSelectionToIndex(treeState.delegate.keys.indexOfFirst { it.key == key })
                    }
                }

                pointerEvent.keyboardModifiers.isCtrlPressed -> {
                    Log.t("control pressed")
                    treeState.lastKeyEventUsedMouse = false
                    scope.launch {
                        treeState.toggleElementSelection(treeState.delegate.keys.indexOfFirst { it.key == key })
                    }
                }

                else -> {
                    val element = treeState.flattenedTree[treeState.delegate.keys.indexOfFirst { it.key == key }]
                    Log.e(treeState.toString())
                    @Suppress("UNCHECKED_CAST")
                    notifyItemClicked(
                        item = element as Tree.Element<T>,
                        scope = scope,
                        doubleClickTimeDelayMillis = platformDoubleClickDelay,
                        onElementClick = onElementClick,
                        onElementDoubleClick = onElementDoubleClick
                    )
                    scope.launch {
                        treeState.delegate.selectSingleKey(key, skipScroll = true)
                    }
                }
            }
        }
    }

    // todo warning: this is ugly workaround
    // for item click that lose focus and fail to match if a operation is a double-click
    var elementClickedTmpHolder: Tree.Element<*>? = null
    internal fun <T> notifyItemClicked(
        item: Tree.Element<T>,
        scope: CoroutineScope,
        doubleClickTimeDelayMillis: Long,
        onElementClick: (Tree.Element<T>) -> Unit,
        onElementDoubleClick: (Tree.Element<T>) -> Unit
    ) {
        if (elementClickedTmpHolder?.id == item.id) {
            // is a double click
            if (item is Tree.Element.Node) {
                treeState.toggleNode(item)
            }
            onElementDoubleClick(item)
            elementClickedTmpHolder = null
            Log.d("doubleClicked!")
        } else {
            elementClickedTmpHolder = item
            // is a single click
            onElementClick(item)
            scope.launch {
                delay(doubleClickTimeDelayMillis)
                if (elementClickedTmpHolder == item) elementClickedTmpHolder = null
            }

            Log.d("singleClicked!")
        }
    }
}

class DefaultTreeViewKeyActions(treeState: TreeState) : DefaultSelectableLazyColumnKeyActions(treeState.delegate) {

    override val keybindings: TreeViewKeybindings = DefaultTreeViewKeybindings
    override val actions: DefaultTreeViewOnKeyEvent = DefaultTreeViewOnKeyEvent(keybindings, treeState = treeState)

    override fun handleOnKeyEvent(coroutineScope: CoroutineScope): KeyEvent.(Int) -> Boolean = lambda@{ focusedIndex ->
        if (type == KeyEventType.KeyUp) return@lambda false
        val keyEvent = this
        with(keybindings) {
            with(actions) {
                Log.d(keyEvent.key.keyCode.toString())
                when {
                    extendSelectionToChild() ?: false -> coroutineScope.launch { onExtendSelectionToChild(focusedIndex) }
                    extendSelectionToParent() ?: false -> coroutineScope.launch { onExtendSelectionToParent(focusedIndex) }
                    selectNextSibling() ?: false -> coroutineScope.launch { onSelectNextSibling(focusedIndex) }
                    selectPreviousSibling() ?: false -> coroutineScope.launch { onSelectPreviousSibling(focusedIndex) }
                    selectParent() ?: false -> coroutineScope.launch { onSelectParent(focusedIndex) }
                    selectChild() ?: false -> coroutineScope.launch { onSelectChild(focusedIndex) }
                    super.handleOnKeyEvent(coroutineScope).invoke(keyEvent, focusedIndex) -> return@lambda true
                    else -> return@lambda false
                }
            }
        }
        return@lambda true
    }
}

open class DefaultSelectableLazyColumnKeyActions(val selectableState: SelectableLazyListState) : KeyBindingScopedActions {

    override val keybindings: SelectableColumnKeybindings
        get() = DefaultSelectableColumnKeybindings

    override val actions: SelectableColumnOnKeyEvent
        get() = DefaultSelectableOnKeyEvent(keybindings, selectableState)

    override fun handleOnKeyEvent(coroutineScope: CoroutineScope): KeyEvent.(Int) -> Boolean =
        lambda@{ focusedIndex ->
            if (type == KeyEventType.KeyUp) return@lambda false
            with(keybindings) {
                with(actions) {
                    with(coroutineScope) {
                        execute(focusedIndex)
                    }
                }
            }
        }

    context(CoroutineScope, SelectableColumnKeybindings, SelectableColumnOnKeyEvent)
    private fun KeyEvent.execute(focusedIndex: Int): Boolean {
        when {
            selectNextItem() ?: false -> launch { onSelectNextItem(focusedIndex) }
            selectPreviousItem() ?: false -> launch { onSelectPreviousItem(focusedIndex) }
            selectFirstItem() ?: false -> launch { onSelectFirstItem() }
            selectLastItem() ?: false -> launch { onSelectLastItem() }
            edit() ?: false -> launch { onEdit(focusedIndex) }
            extendSelectionToFirstItem() ?: false -> launch { onExtendSelectionToFirst(focusedIndex) }
            extendSelectionToLastItem() ?: false -> launch { onExtendSelectionToLastItem(focusedIndex) }
            extendSelectionWithNextItem() ?: false -> launch { onExtendSelectionWithNextItem(focusedIndex) }
            extendSelectionWithPreviousItem() ?: false -> launch { onExtendSelectionWithPreviousItem(focusedIndex) }
            scrollPageDownAndExtendSelection() ?: false -> launch { onScrollPageDownAndExtendSelection(focusedIndex) }
            scrollPageDownAndSelectItem() ?: false -> launch { onScrollPageDownAndSelectItem(focusedIndex) }
            scrollPageUpAndExtendSelection() ?: false -> launch { onScrollPageUpAndExtendSelection(focusedIndex) }
            scrollPageUpAndSelectItem() ?: false -> launch { onScrollPageUpAndSelectItem(focusedIndex) }
            else -> return false
        }
        return true
    }
}
