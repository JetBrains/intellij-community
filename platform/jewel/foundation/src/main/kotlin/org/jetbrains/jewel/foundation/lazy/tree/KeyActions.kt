package org.jetbrains.jewel.foundation.lazy.tree

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
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

/** Combines keybindings and on-key-event actions for a selectable list or tree, dispatching keyboard events. */
public interface KeyActions {
    /** The keybindings that define which key combinations trigger selection actions. */
    public val keybindings: SelectableColumnKeybindings

    /** The handler that performs selection actions in response to key events. */
    public val actions: SelectableColumnOnKeyEvent

    /**
     * Returns a handler lambda for [event] that, when invoked on the [KeyEvent], applies the appropriate selection
     * action and returns `true` if the event was consumed, or `false` to let it propagate.
     *
     * @param event The incoming key event.
     * @param keys All selectable keys in the current list.
     * @param state The mutable selection and scroll state.
     * @param selectionMode The active selection mode.
     */
    public fun handleOnKeyEvent(
        event: KeyEvent,
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    ): KeyEvent.() -> Boolean
}

/**
 * Handles pointer (mouse) events for a selectable list or tree, including click-based selection and range extension.
 */
public interface PointerEventActions {
    /**
     * Handles a pointer press event, updating selection in [selectableLazyListState] for [key] based on modifier keys
     * and [selectionMode].
     *
     * @param pointerEvent The raw pointer event containing modifier key state.
     * @param keybindings The keybindings defining which modifier keys trigger multi/range select.
     * @param selectableLazyListState The mutable selection state to update.
     * @param selectionMode The active selection mode.
     * @param allKeys All selectable keys in the list.
     * @param key The key of the item that was pressed.
     */
    public fun handlePointerEventPress(
        pointerEvent: PointerEvent,
        keybindings: SelectableColumnKeybindings,
        selectableLazyListState: SelectableLazyListState,
        selectionMode: SelectionMode,
        allKeys: List<SelectableLazyListKey>,
        key: Any,
    )

    /**
     * Toggles the selection state of [key] in [selectableLazyListState], respecting [selectionMode].
     *
     * @param key The key to toggle.
     * @param allKeys All selectable keys in the list.
     * @param selectableLazyListState The mutable selection state to update.
     * @param selectionMode The active selection mode.
     */
    public fun toggleKeySelection(
        key: Any,
        allKeys: List<SelectableLazyListKey>,
        selectableLazyListState: SelectableLazyListState,
        selectionMode: SelectionMode,
    )

    /**
     * Extends the current selection from the active item to [key], adding all items in between.
     *
     * @param key The target key to extend selection to.
     * @param allKeys All selectable keys in the list.
     * @param state The mutable selection state to update.
     * @param selectionMode The active selection mode.
     */
    public fun onExtendSelectionToKey(
        key: Any,
        allKeys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    )
}

/** Default [PointerEventActions] for selectable lazy columns, handling click, multi-select, and range-select. */
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
                    if (selectionMode == SelectionMode.None) return

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
        selectableLazyListState.lastActiveItemIndex = allKeys.indexOfFirst { it.key == key }
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

/** [DefaultSelectableLazyColumnEventAction] for tree views, adding single/double-click detection for node expansion. */
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
        if (selectionMode == SelectionMode.None) return

        // When mouse is used, we're no longer in keyboard navigation mode
        selectableLazyListState.isKeyboardNavigating = false

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
                    selectableLazyListState.lastActiveItemIndex = allKeys.indexOfFirst { it.key == key }
                }
            }
        }
    }

    // todo warning: move this away from here
    // for item click that lose focus and fail to match if a operation is a double-click
    private var elementClickedTmpHolder: Any? = null

    /**
     * Detects whether [item] was single-clicked or double-clicked within [doubleClickTimeDelayMillis] and invokes the
     * appropriate callback. For double-clicks on a [Tree.Element.Node], the node is also toggled.
     *
     * @param T The type of data in the tree.
     * @param item The tree element that was clicked.
     * @param scope The coroutine scope used to launch the double-click timeout.
     * @param doubleClickTimeDelayMillis The window in milliseconds within which a second click counts as a
     *   double-click.
     * @param onElementClick Callback invoked on a single click.
     * @param onElementDoubleClick Callback invoked on a double click.
     */
    @ApiStatus.Internal
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

/**
 * Creates a [DefaultTreeViewKeyActions] with platform-appropriate keybindings (macOS or Windows/Linux).
 *
 * @param treeState The [TreeState] used by key actions to expand/collapse nodes.
 */
public fun DefaultTreeViewKeyActions(treeState: TreeState): DefaultTreeViewKeyActions {
    val keybindings =
        when {
            hostOs.isMacOS -> DefaultMacOsTreeColumnKeybindings
            else -> DefaultTreeViewKeybindings
        }
    return DefaultTreeViewKeyActions(keybindings, DefaultTreeViewOnKeyEvent(keybindings, treeState))
}

/**
 * [KeyActions] for tree views, adding expand/collapse key handling on top of [DefaultSelectableLazyColumnKeyActions].
 */
public class DefaultTreeViewKeyActions(
    /** The tree-view-specific keybindings, including expand and collapse key mappings. */
    override val keybindings: TreeViewKeybindings,
    /** The handler that performs tree-view actions (expand, collapse, selection) in response to key events. */
    override val actions: DefaultTreeViewOnKeyEvent,
) : DefaultSelectableLazyColumnKeyActions(keybindings, actions) {
    override fun handleOnKeyEvent(
        event: KeyEvent,
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    ): KeyEvent.() -> Boolean = lambda@{
        // Explicitly don't handle Tab key events - let them pass through for focus traversal
        if (key == Key.Tab) return@lambda false

        if (type == KeyEventType.KeyUp) return@lambda false
        val keyEvent = this

        // Always mark keyboard navigation mode active for all keyboard interactions
        state.lastKeyEventUsedMouse = false
        state.isKeyboardNavigating = true

        with(keybindings) {
            with(actions) {
                when {
                    selectionMode == SelectionMode.None -> return@lambda false
                    isSelectParent -> onSelectParent(keys, state)
                    isSelectChild -> onSelectChild(keys, state)
                    else -> return@lambda super.handleOnKeyEvent(event, keys, state, selectionMode).invoke(keyEvent)
                }
            }
        }
        return@lambda true
    }
}

/**
 * Default [KeyActions] for selectable lazy columns, dispatching key events via configurable [keybindings] and
 * [actions].
 */
public open class DefaultSelectableLazyColumnKeyActions(
    /** The keybindings that define which key combinations trigger selection actions. */
    override val keybindings: SelectableColumnKeybindings,
    /** The handler that performs selection actions in response to key events. */
    override val actions: SelectableColumnOnKeyEvent = DefaultSelectableOnKeyEvent(keybindings),
) : KeyActions {
    /** The default singleton instance using platform-appropriate keybindings (macOS or Windows/Linux). */
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
        // Explicitly don't handle Tab key events - let them pass through for focus traversal
        if (key == Key.Tab) return@lambda false

        if (type == KeyEventType.KeyUp || selectionMode == SelectionMode.None) return@lambda false

        // More aggressively mark keyboard navigation for all key interactions
        // This improves screen reader behavior
        state.lastKeyEventUsedMouse = false
        state.isKeyboardNavigating = true

        execute(
            keys = keys,
            state = state,
            selectionMode = selectionMode,
            keyEvent = actions,
            keyBindings = keybindings,
        )
    }

    private fun KeyEvent.execute(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
        keyEvent: SelectableColumnOnKeyEvent,
        keyBindings: SelectableColumnKeybindings,
    ): Boolean {
        val singleSelectionEventHandled = handleSingleSelectionEvents(keys, state, keyEvent, keyBindings)
        if (singleSelectionEventHandled) {
            return true
        }
        if (selectionMode == SelectionMode.Multiple) {
            val multipleSelectionEventHandled = handleMultipleSelectionEvents(keys, state, keyEvent, keyBindings)
            if (multipleSelectionEventHandled) {
                return true
            }
        }
        return false
    }

    private fun KeyEvent.handleSingleSelectionEvents(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        keyEvent: SelectableColumnOnKeyEvent,
        keybindings: SelectableColumnKeybindings,
    ): Boolean {
        with(keybindings) {
            when {
                isSelectNextItem -> keyEvent.onSelectNextItem(keys, state)
                isSelectPreviousItem -> keyEvent.onSelectPreviousItem(keys, state)
                isSelectFirstItem -> keyEvent.onSelectFirstItem(keys, state)
                isSelectLastItem -> keyEvent.onSelectLastItem(keys, state)
                isEdit -> keyEvent.onEdit()
                else -> return false
            }
            return true
        }
    }

    private fun KeyEvent.handleMultipleSelectionEvents(
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        keyEvent: SelectableColumnOnKeyEvent,
        keyBindings: SelectableColumnKeybindings,
    ): Boolean {
        with(keyBindings) {
            when {
                isExtendSelectionToFirstItem -> keyEvent.onExtendSelectionToFirst(keys, state)
                isExtendSelectionToLastItem -> keyEvent.onExtendSelectionToLastItem(keys, state)
                isExtendSelectionWithNextItem -> keyEvent.onExtendSelectionWithNextItem(keys, state)
                isExtendSelectionWithPreviousItem -> keyEvent.onExtendSelectionWithPreviousItem(keys, state)
                isScrollPageDownAndExtendSelection -> keyEvent.onScrollPageDownAndExtendSelection(keys, state)
                isScrollPageDownAndSelectItem -> keyEvent.onScrollPageDownAndSelectItem(keys, state)
                isScrollPageUpAndExtendSelection -> keyEvent.onScrollPageUpAndExtendSelection(keys, state)
                isScrollPageUpAndSelectItem -> keyEvent.onScrollPageUpAndSelectItem(keys, state)
                isSelectAll -> keyEvent.onSelectAll(keys, state)
                else -> return false
            }
            return true
        }
    }
}

/**
 * A no-op [KeyActions] whose [handleOnKeyEvent] always returns a handler that consumes nothing (false), so no key event
 * is ever handled. For internal use when keyboard input handling must be disabled.
 */
@ApiStatus.Internal
@InternalJewelApi
public object NoopListKeyActions : KeyActions {
    /** The keybindings used by this no-op implementation (default column keybindings). */
    override val keybindings: SelectableColumnKeybindings
        get() = DefaultSelectableColumnKeybindings

    /** The on-key-event handler used by this no-op implementation. */
    override val actions: SelectableColumnOnKeyEvent = DefaultSelectableOnKeyEvent(keybindings)

    override fun handleOnKeyEvent(
        event: KeyEvent,
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    ): KeyEvent.() -> Boolean = { false }
}
