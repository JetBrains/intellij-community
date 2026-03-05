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
import androidx.compose.ui.input.pointer.PointerEvent
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
import org.jetbrains.jewel.foundation.util.JewelLogger

private val logger = JewelLogger.getInstance("org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn")

/**
 * Displays a lazily composed column with single-selection behavior.
 *
 * Use this API with [SingleSelectionLazyListState], usually created via [rememberSingleSelectionLazyListState].
 *
 * @param modifier The [Modifier] applied to the underlying [LazyColumn] container.
 * @param state The single-selection state holder for scroll and selection behavior.
 * @param contentPadding Inner padding applied around the content.
 * @param reverseLayout Whether items are laid out in reverse order (bottom-to-top).
 * @param onSelectedIndexesChange Callback invoked when selected indices change after initial composition.
 * @param verticalArrangement Vertical arrangement of items. Defaults to [Arrangement.Top] unless [reverseLayout] is
 *   `true`, in which case it defaults to [Arrangement.Bottom].
 * @param horizontalAlignment Horizontal alignment for items in the column.
 * @param flingBehavior Fling behavior used by the underlying [LazyColumn].
 * @param keyActions Keyboard interaction handlers used for selection/navigation behavior.
 * @param pointerEventActions Pointer interaction handlers used for selection behavior.
 * @param interactionSource Optional [MutableInteractionSource] to observe focus/interaction state. If `null`, an
 *   internal source is created.
 * @param content The list content declared in [SelectableLazyListScope].
 */
@Composable
public fun SingleSelectionLazyColumn(
    modifier: Modifier = Modifier,
    state: SingleSelectionLazyListState = rememberSingleSelectionLazyListState(),
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
    SelectableLazyColumnImpl(
        modifier = modifier,
        selectionMode = SelectionMode.Single,
        state = state.delegate,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        onSelectedIndexesChange = onSelectedIndexesChange,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        keyActions = keyActions,
        pointerEventActions = pointerEventActions,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * Displays a lazily composed column with multiple-selection behavior.
 *
 * Use this API with [MultiSelectionLazyListState], usually created via [rememberMultiSelectionLazyListState].
 *
 * @param modifier The [Modifier] applied to the underlying [LazyColumn] container.
 * @param state The multi-selection state holder for scroll and selection behavior.
 * @param contentPadding Inner padding applied around the content.
 * @param reverseLayout Whether items are laid out in reverse order (bottom-to-top).
 * @param onSelectedIndexesChange Callback invoked when selected indices change after initial composition.
 * @param verticalArrangement Vertical arrangement of items. Defaults to [Arrangement.Top] unless [reverseLayout] is
 *   `true`, in which case it defaults to [Arrangement.Bottom].
 * @param horizontalAlignment Horizontal alignment for items in the column.
 * @param flingBehavior Fling behavior used by the underlying [LazyColumn].
 * @param keyActions Keyboard interaction handlers used for selection/navigation behavior.
 * @param pointerEventActions Pointer interaction handlers used for selection behavior.
 * @param interactionSource Optional [MutableInteractionSource] to observe focus/interaction state. If `null`, an
 *   internal source is created.
 * @param content The list content declared in [SelectableLazyListScope].
 */
@Composable
public fun MultiSelectionLazyColumn(
    modifier: Modifier = Modifier,
    state: MultiSelectionLazyListState = rememberMultiSelectionLazyListState(),
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
    SelectableLazyColumnImpl(
        modifier = modifier,
        selectionMode = SelectionMode.Multiple,
        state = state.delegate,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        onSelectedIndexesChange = onSelectedIndexesChange,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        keyActions = keyActions,
        pointerEventActions = pointerEventActions,
        interactionSource = interactionSource,
        content = content,
    )
}

/** A composable that displays a scrollable and selectable list of items in a column arrangement. */
@Composable
@Deprecated(
    message =
        "Migrate to SingleSelectionLazyColumn or MultiSelectionLazyColumn and use the matching " +
            "rememberSingleSelectionLazyListState(...) or rememberMultiSelectionLazyListState(...)."
)
public fun SelectableLazyColumn(
    modifier: Modifier = Modifier,
    selectionMode: SelectionMode = SelectionMode.Multiple,
    state: SelectableLazyListState = rememberSelectableLazyListState(selectionMode = selectionMode),
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
    SelectableLazyColumnImpl(
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
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
@Deprecated(
    message =
        "Migrate to SingleSelectionLazyColumn or MultiSelectionLazyColumn and use the matching " +
            "rememberSingleSelectionLazyListState(...) or rememberMultiSelectionLazyListState(...).",
    level = DeprecationLevel.HIDDEN,
)
public fun SelectableLazyColumn(
    modifier: Modifier = Modifier,
    selectionMode: SelectionMode = SelectionMode.Multiple,
    state: SelectableLazyListState = rememberSelectableLazyListState(selectionMode = selectionMode),
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

/**
 * A composable function that implements a lazy column with selection capabilities. This method allows multiple or
 * single item selection based on the specified selection mode and offers advanced configuration like content alignment,
 * key action handling, pointer event handling, and state management.
 *
 * @param modifier A [Modifier] that will be applied to the lazy column.
 * @param selectionMode The mode of selection, which can be [SelectionMode.Multiple] for multi-selection or
 *   [SelectionMode.None] to disable selection.
 * @param state The state object defining the current selection and configuration of the lazy column. Use
 *   [rememberSelectableLazyListState] to create or remember the default state if none is provided.
 * @param contentPadding The padding values for the content displayed inside the lazy column.
 * @param reverseLayout A boolean indicating whether to reverse the layout of the column (i.e., bottom-to-top).
 * @param onSelectedIndexesChange A callback invoked when the selected indices change. It provides the list of currently
 *   selected indices.
 * @param verticalArrangement Specifies the spacing and arrangement of items in the vertical direction. Defaults to
 *   [Arrangement.Top] for non-reversed layouts or [Arrangement.Bottom] for reversed layouts.
 * @param horizontalAlignment Aligns the horizontal position of items within the column. Defaults to [Alignment.Start].
 * @param flingBehavior The behavior that determines how the lazy column will respond to fling gestures. Defaults to
 *   [ScrollableDefaults.flingBehavior].
 * @param keyActions Key bindings for handling keyboard interactions and selection behaviors in the lazy column.
 *   Defaults to [DefaultSelectableLazyColumnKeyActions].
 * @param pointerEventActions Event handlers for pointer-based interactions (e.g., mouse or touch input). Defaults to
 *   [DefaultSelectableLazyColumnEventAction].
 * @param interactionSource Optional [MutableInteractionSource] that stores interaction state and events, such as hover
 *   or focus. If none is provided, a new one will be remembered and managed internally.
 * @param content The lambda defining the content to be displayed within the lazy column. Use the
 *   [SelectableLazyListScope] receiver to define items and content behavior.
 */
@Composable
private fun SelectableLazyColumnImpl(
    modifier: Modifier = Modifier,
    selectionMode: SelectionMode = SelectionMode.Multiple,
    state: SelectableLazyListState = rememberSelectableLazyListState(selectionMode = selectionMode),
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
    // Legacy compatibility contract:
    // `SelectableLazyColumn` historically accepts both `selectionMode` and `state`, so callers can pass values
    // that disagree (for example: selectionMode=Single, state.selectionMode=Multiple).
    //
    // In this legacy API, the explicit `selectionMode` argument remains authoritative for interaction behavior
    // (keyboard/pointer handlers). We intentionally do not rewrite state during composition.
    // Instead, we warn and preserve current state values for rendering/callback baseline to avoid unnecessary
    // recompositions and side-effects.
    //
    // The typed entry points (`SingleSelectionLazyColumn` and `MultiSelectionLazyColumn`) avoid this ambiguity.
    LogLegacySelectionModeWarnings(selectionMode = selectionMode, state = state)

    val effectiveSelectionMode = selectionMode
    val intSource = interactionSource ?: remember { MutableInteractionSource() }

    val scope = rememberCoroutineScope()
    val latestContent = rememberUpdatedState(content)
    val containerState by remember {
        derivedStateOf(referentialEqualityPolicy()) { SelectableLazyListScopeContainer().apply(latestContent.value) }
    }
    val container = containerState

    val keys = remember(container) { container.getKeys() }
    val isFocused by intSource.collectIsFocusedAsState()

    // Tracks last emitted indices to dedupe commit-time/effect-driven updates.
    // Seed once from the composition-time selection snapshot:
    // - prevents initial preselected keys from emitting on first composition
    // - still allows early programmatic selection changes (after composition starts) to emit
    var lastEmittedIndices by remember {
        mutableStateOf(state.selectedKeys.mapNotNull { key -> container.getKeyIndex(key) })
    }

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

            // Keep the keyboard navigation gate in sync after key→index remaps, including through empty→non‑empty
            // transitions. Preserve an existing active index when it is still selected; otherwise fall back to
            // the first selected index.
            if (indices.isNotEmpty()) {
                val activeIndex = state.lastActiveItemIndex
                state.lastActiveItemIndex =
                    if (activeIndex != null && activeIndex in indices) activeIndex else indices.first()
            }

            // Notify using the latest callback reference to avoid capturing a stale lambda.
            latestOnSelectedIndexesChange.value(indices)
        }
    }

    LaunchedEffect(isFocused) {
        if (!isFocused || effectiveSelectionMode == SelectionMode.None) return@LaunchedEffect
        with(state) {
            if (lastActiveItemIndex == null && selectedKeys.isEmpty()) {
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
                    pointerEvent: PointerEvent,
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
                        notifyingKeyActions.handleOnKeyEvent(event, keys, state, effectiveSelectionMode).invoke(event)
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
                effectiveSelectionMode,
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

/**
 * Logs mismatch warnings for legacy `SelectableLazyColumn` mode/state inputs.
 *
 * This helper isolates deprecation-compatibility diagnostics from core selection logic.
 *
 * Emitted warnings:
 * - Generic mismatch: `selectionMode != state.selectionMode` (legacy precedence applies).
 * - Edge-case mismatch:
 *     - `selectionMode = None` while state already contains selected keys.
 *     - `selectionMode = Single` while state contains multiple selected keys.
 *
 * We only warn; we do not mutate state here. That keeps composition side-effect free with respect to selection data and
 * avoids recomposition churn from compatibility normalization.
 */
@Composable
private fun LogLegacySelectionModeWarnings(selectionMode: SelectionMode, state: SelectableLazyListState) {
    val modeMismatch = selectionMode != state.selectionMode

    LaunchedEffect(selectionMode, state.selectionMode) {
        if (!modeMismatch) return@LaunchedEffect
        logger.warn(
            "SelectableLazyColumn: selectionMode=$selectionMode does not match state.selectionMode=" +
                "${state.selectionMode}. selectionMode will be used."
        )
    }

    LaunchedEffect(selectionMode, state.selectionMode, state.selectedKeys.size) {
        if (!modeMismatch) return@LaunchedEffect
        when {
            selectionMode == SelectionMode.None && state.selectedKeys.isNotEmpty() -> {
                logger.warn(
                    "SelectableLazyColumn: selectionMode=${SelectionMode.None} " +
                        "while state has ${state.selectedKeys.size} selected key(s). " +
                        "Initial selection may appear inconsistent until user interaction."
                )
            }
            selectionMode == SelectionMode.Single && state.selectedKeys.size > 1 -> {
                logger.warn(
                    "SelectableLazyColumn: selectionMode=${SelectionMode.Single} " +
                        "while state has ${state.selectedKeys.size} selected key(s). " +
                        "Initial selection may appear inconsistent until user interaction."
                )
            }
        }
    }
}
