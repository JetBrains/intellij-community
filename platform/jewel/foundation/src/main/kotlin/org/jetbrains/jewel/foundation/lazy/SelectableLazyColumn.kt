package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListScopeContainer.Entry
import org.jetbrains.jewel.foundation.lazy.tree.DefaultSelectableLazyColumnEventAction
import org.jetbrains.jewel.foundation.lazy.tree.DefaultSelectableLazyColumnKeyActions
import org.jetbrains.jewel.foundation.lazy.tree.KeyActions
import org.jetbrains.jewel.foundation.lazy.tree.PointerEventActions

@Composable
@Deprecated("Use SelectableLazyColumn with 'interactionSource' parameter instead", level = DeprecationLevel.HIDDEN)
public fun SelectableLazyColumn(
    modifier: Modifier = Modifier,
    selectionMode: SelectionMode = SelectionMode.Multiple,
    state: SelectableLazyListState = rememberSelectableLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    onSelectedIndexesChange: (List<Int>) -> Unit = {},
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    keyActions: KeyActions = DefaultSelectableLazyColumnKeyActions,
    pointerEventActions: PointerEventActions = DefaultSelectableLazyColumnEventAction(),
    content: SelectableLazyListScope.() -> Unit,
) {
    SelectableLazyColumn(
        modifier = modifier,
        selectionMode = selectionMode,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        onSelectedIndexesChange = onSelectedIndexesChange,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        keyActions = keyActions,
        pointerEventActions = pointerEventActions,
        interactionSource = null,
        content = content,
    )
}

/** A composable that displays a scrollable and selectable list of items in a column arrangement. */
@Composable
public fun SelectableLazyColumn(
    modifier: Modifier = Modifier,
    selectionMode: SelectionMode = SelectionMode.Multiple,
    state: SelectableLazyListState = rememberSelectableLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    onSelectedIndexesChange: (List<Int>) -> Unit = {},
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    keyActions: KeyActions = DefaultSelectableLazyColumnKeyActions,
    pointerEventActions: PointerEventActions = DefaultSelectableLazyColumnEventAction(),
    interactionSource: MutableInteractionSource? = null,
    content: SelectableLazyListScope.() -> Unit,
) {
    val intSource = interactionSource ?: remember { MutableInteractionSource() }

    val scope = rememberCoroutineScope()
    val latestContent = rememberUpdatedState(content)
    val containerState by remember {
        derivedStateOf(referentialEqualityPolicy()) { SelectableLazyListScopeContainer().apply(latestContent.value) }
    }
    val container = containerState

    val keys = remember(container) { container.getKeys() }
    val isFocused by intSource.collectIsFocusedAsState()

    /** Tracks the last emitted indices to avoid duplicate emissions from both commit-time and effect-driven updates. */
    var lastEmittedIndices by remember { mutableStateOf<List<Int>>(emptyList()) }

    // Keep the latest callback reference to avoid capturing a stale lambda inside effects
    val latestOnSelectedIndexesChange = rememberUpdatedState(onSelectedIndexesChange)

    // Secondary emission path: mapping-change and programmatic-selection bridge
    //
    // We have two ways to emit selected indices to callers:
    // 1) Primary (synchronous, commit-time) — the notifying wrappers around
    //    KeyActions/PointerEventActions (see `notifyingKeyActions`/`notifyingPointerEventActions`).
    //    They emit immediately when the user commits a selection via keyboard or mouse. This is
    //    crucial for flows where the popup disposes right after the commit (e.g., dropdown click
    //    or Enter), because an effect might never get a chance to run.
    // 2) Secondary (this effect) — runs when the key→index mapping changes (e.g., reorder, header
    //    insert/remove, filtering) or when `selectedKeys` is updated programmatically. In these cases
    //    there’s no user "commit" event to intercept, but consumers that rely on indices must stay in
    //    sync. We recompute indices from keys and only notify if they differ from the last emission.
    //
    // Additionally, we synchronize the keyboard-navigation anchor (`lastActiveItemIndex`) after remaps
    // so the very next arrow key works reliably, including across empty→non‑empty transitions.
    LaunchedEffect(container, state.selectedKeys) {
        val selectedKeysSnapshot = state.selectedKeys
        val indices = selectedKeysSnapshot.mapNotNull { key -> container.getKeyIndex(key) }
        if (indices != lastEmittedIndices) {
            lastEmittedIndices = indices

            // Keep keyboard navigation gate in sync after key→index remaps, including through empty→non‑empty
            // transitions.
            if (indices.isNotEmpty()) {
                state.lastActiveItemIndex = indices.first()
            }

            // Notify using the latest callback reference to avoid capturing a stale lambda.
            latestOnSelectedIndexesChange.value(indices)
        }
    }

    LaunchedEffect(isFocused) {
        with(state) {
            if (isFocused && lastActiveItemIndex == null && selectedKeys.isEmpty()) {
                keyActions.actions.onSelectFirstItem(keys, this)
            }
        }
    }

    // Synchronous commit-time emission for pointer interactions
    //
    // This wrapper delegates to the provided `PointerEventActions` but also emits `onSelectedIndexesChange`
    // immediately after a user commit (press/toggle/extend) if the selection actually changed. We:
    //  - Compare `state.selectedKeys` before/after by identity to detect real changes (the state replaces the set).
    //  - Translate keys→indices via the current `container` so callers that want indices receive them right away.
    //  - Deduplicate with `lastEmittedIndices` so both this synchronous path and the effect path don’t double emit.
    //
    // Why a wrapper? In flows like a List/ComboBox dropdown, the popup is disposed as a consequence of the click.
    // If we waited for an effect to run, the component might already be gone and the emission would be lost.
    val notifyingPointerEventActions =
        remember(pointerEventActions, container, state, onSelectedIndexesChange) {
            object : PointerEventActions {
                private fun emitIfSelectionChanged(before: Set<Any>) {
                    state.selectedIndicesIfChanged(before, container, lastEmittedIndices)?.let { indices ->
                        lastEmittedIndices = indices
                        onSelectedIndexesChange(indices)
                    }
                }

                override fun handlePointerEventPress(
                    pointerEvent: androidx.compose.ui.input.pointer.PointerEvent,
                    keybindings: SelectableColumnKeybindings,
                    selectableLazyListState: SelectableLazyListState,
                    selectionMode: SelectionMode,
                    allKeys: List<SelectableLazyListKey>,
                    key: Any,
                ) {
                    val before = state.selectedKeys
                    pointerEventActions.handlePointerEventPress(
                        pointerEvent,
                        keybindings,
                        selectableLazyListState,
                        selectionMode,
                        allKeys,
                        key,
                    )
                    emitIfSelectionChanged(before)
                }

                override fun toggleKeySelection(
                    key: Any,
                    allKeys: List<SelectableLazyListKey>,
                    selectableLazyListState: SelectableLazyListState,
                    selectionMode: SelectionMode,
                ) {
                    val before = state.selectedKeys
                    pointerEventActions.toggleKeySelection(key, allKeys, selectableLazyListState, selectionMode)
                    emitIfSelectionChanged(before)
                }

                override fun onExtendSelectionToKey(
                    key: Any,
                    allKeys: List<SelectableLazyListKey>,
                    state: SelectableLazyListState,
                    selectionMode: SelectionMode,
                ) {
                    val before = state.selectedKeys
                    pointerEventActions.onExtendSelectionToKey(key, allKeys, state, selectionMode)
                    emitIfSelectionChanged(before)
                }
            }
        }

    // Synchronous commit-time emission for keyboard interactions
    //
    // This wrapper decorates `KeyActions` so that, when a key event is handled and the selection actually
    // changes, we emit indices on the spot. This mirrors the pointer wrapper and serves the same purpose:
    // avoid losing the emission if handling the key (e.g., Enter) triggers immediate disposal of the popup.
    //
    // Implementation notes:
    //  - Only emit if the delegate reports `handled` and `selectedKeys` has changed since the event.
    //  - Use the current `container` to compute indices and dedupe with `lastEmittedIndices`.
    val notifyingKeyActions =
        remember(keyActions, container, state, onSelectedIndexesChange) {
            object : KeyActions {
                override val keybindings
                    get() = keyActions.keybindings

                override val actions
                    get() = keyActions.actions

                override fun handleOnKeyEvent(
                    event: KeyEvent,
                    keys: List<SelectableLazyListKey>,
                    state: SelectableLazyListState,
                    selectionMode: SelectionMode,
                ): KeyEvent.() -> Boolean {
                    val delegate = keyActions.handleOnKeyEvent(event, keys, state, selectionMode)
                    return {
                        val before = state.selectedKeys
                        val handled = delegate.invoke(this)
                        if (handled) {
                            state.selectedIndicesIfChanged(before, container, lastEmittedIndices)?.let { indices ->
                                lastEmittedIndices = indices
                                onSelectedIndexesChange(indices)
                            }
                        }
                        handled
                    }
                }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LazyColumn(
        modifier =
            modifier
                .focusProperties { canFocus = true }
                .focusRequester(focusRequester)
                .focusable(enabled = true, intSource)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Tab) {
                        return@onPreviewKeyEvent false
                    }

                    if (state.lastActiveItemIndex == null) {
                        val derivedFromSelection = keys.indexOfFirst { it.key in state.selectedKeys }.takeIf { it >= 0 }
                        val firstSelectable =
                            if (derivedFromSelection == null) {
                                keys.indexOfFirst { it is SelectableLazyListKey.Selectable }.takeIf { it >= 0 }
                            } else {
                                null
                            }
                        state.lastActiveItemIndex = derivedFromSelection ?: firstSelectable
                    }

                    val actionHandled =
                        notifyingKeyActions.handleOnKeyEvent(event, keys, state, selectionMode).invoke(event)
                    if (actionHandled) {
                        scope.launch { state.lastActiveItemIndex?.let { state.scrollToItem(it) } }
                    }
                    actionHandled
                },
        state = state.lazyListState,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
    ) {
        container.getEntries().forEach { entry ->
            appendEntry(
                entry,
                state,
                isFocused,
                keys,
                focusRequester,
                notifyingKeyActions,
                notifyingPointerEventActions,
                selectionMode,
                container::isKeySelectable,
            )
        }
    }
}

private fun LazyListScope.appendEntry(
    entry: Entry,
    state: SelectableLazyListState,
    isFocused: Boolean,
    keys: List<SelectableLazyListKey>,
    focusRequester: FocusRequester,
    keyActions: KeyActions,
    pointerEventActions: PointerEventActions,
    selectionMode: SelectionMode,
    isKeySelectable: (Any) -> Boolean,
) {
    when (entry) {
        is Entry.Item ->
            item(entry.key, entry.contentType) {
                val itemScope =
                    SelectableLazyItemScope(isSelected = entry.key in state.selectedKeys, isActive = isFocused)
                if (isKeySelectable(entry.key)) {
                    Box(
                        modifier =
                            Modifier.selectable(
                                requester = focusRequester,
                                keybindings = keyActions.keybindings,
                                actionHandler = pointerEventActions,
                                selectionMode = selectionMode,
                                selectableState = state,
                                allKeys = keys,
                                itemKey = entry.key,
                            )
                    ) {
                        entry.content.invoke(itemScope)
                    }
                } else {
                    entry.content.invoke(itemScope)
                }
            }
        is Entry.Items ->
            items(count = entry.count, key = { entry.key(it) }, contentType = { entry.contentType(it) }) { index ->
                val key = remember(entry, index) { entry.key(index) }
                val itemScope = SelectableLazyItemScope(key in state.selectedKeys, isFocused)
                if (isKeySelectable(key)) {
                    Box(
                        modifier =
                            Modifier.selectable(
                                requester = focusRequester,
                                keybindings = keyActions.keybindings,
                                actionHandler = pointerEventActions,
                                selectionMode = selectionMode,
                                selectableState = state,
                                allKeys = keys,
                                itemKey = entry.key(index),
                            )
                    ) {
                        entry.itemContent.invoke(itemScope, index)
                    }
                } else {
                    entry.itemContent.invoke(itemScope, index)
                }
            }
        is Entry.StickyHeader ->
            stickyHeader(entry.key, entry.contentType) {
                val itemScope = SelectableLazyItemScope(entry.key in state.selectedKeys, isFocused)

                if (isKeySelectable(entry.key)) {
                    Box(
                        modifier =
                            Modifier.selectable(
                                keybindings = keyActions.keybindings,
                                actionHandler = pointerEventActions,
                                selectionMode = selectionMode,
                                selectableState = state,
                                allKeys = keys,
                                itemKey = entry.key,
                            )
                    ) {
                        entry.content.invoke(itemScope)
                    }
                } else {
                    SelectableLazyItemScope(entry.key in state.selectedKeys, isFocused).apply {
                        entry.content.invoke(itemScope)
                    }
                }
            }
    }
}

private fun Modifier.selectable(
    requester: FocusRequester? = null,
    keybindings: SelectableColumnKeybindings,
    actionHandler: PointerEventActions,
    selectionMode: SelectionMode,
    selectableState: SelectableLazyListState,
    allKeys: List<SelectableLazyListKey>,
    itemKey: Any,
) =
    // Prevent this item from being individually focusable by Tab
    focusProperties {
            // Make items unfocusable by Tab focus traversal
            canFocus = false
        }
        // Add semantics for accessibility
        .semantics(mergeDescendants = true) {
            selected = itemKey in selectableState.selectedKeys
            focused = selectableState.lastActiveItemIndex == allKeys.indexOfFirst { it.key == itemKey }
            stateDescription = ""
            isTraversalGroup = false
        }
        // Handle pointer input but ensure Tab keys aren't intercepted
        .pointerInput(allKeys, itemKey) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                        PointerEventType.Press -> {
                            actionHandler.handlePointerEventPress(
                                pointerEvent = event,
                                keybindings = keybindings,
                                selectableLazyListState = selectableState,
                                selectionMode = selectionMode,
                                allKeys = allKeys,
                                key = itemKey,
                            )
                            requester?.requestFocus()
                        }
                    }
                }
            }
        }

// Computes selected indices if `selectedKeys` changed by identity; returns new indices or null if unchanged.
private fun SelectableLazyListState.selectedIndicesIfChanged(
    before: Set<Any>,
    container: SelectableLazyListScopeContainer,
    lastEmitted: List<Int>,
): List<Int>? {
    val after = selectedKeys
    if (before !== after) {
        val indices = after.mapNotNull { container.getKeyIndex(it) }
        if (indices != lastEmitted) return indices
    }
    return null
}
