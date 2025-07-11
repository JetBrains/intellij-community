package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.focused
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
    content: SelectableLazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val latestContent = rememberUpdatedState(content)
    val containerState by remember {
        derivedStateOf(referentialEqualityPolicy()) { SelectableLazyListScopeContainer().apply(latestContent.value) }
    }
    val container = containerState

    val keys = remember(container) { container.getKeys() }
    var isFocused by remember { mutableStateOf(false) }

    var lastSelectedKeys by remember { mutableStateOf(state.selectedKeys) }
    LaunchedEffect(state.selectedKeys, onSelectedIndexesChange, container) {
        if (lastSelectedKeys == state.selectedKeys) return@LaunchedEffect

        val indices = state.selectedKeys.mapNotNull { key -> container.getKeyIndex(key) }
        lastSelectedKeys = state.selectedKeys
        onSelectedIndexesChange(indices)
    }

    val focusRequester = remember { FocusRequester() }
    LazyColumn(
        modifier =
            modifier
                .onFocusChanged {
                    isFocused = it.hasFocus
                    with(state) {
                        if (isFocused && lastActiveItemIndex == null && selectedKeys.isEmpty()) {
                            keyActions.actions.onSelectFirstItem(keys, this)
                        }
                    }
                }
                .focusProperties { canFocus = true }
                .focusRequester(focusRequester)
                .focusable(enabled = true)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Tab) {
                        return@onPreviewKeyEvent false
                    }

                    if (state.lastActiveItemIndex != null) {
                        val actionHandled = keyActions.handleOnKeyEvent(event, keys, state, selectionMode).invoke(event)
                        if (actionHandled) {
                            scope.launch { state.lastActiveItemIndex?.let { state.scrollToItem(it) } }
                        }
                        return@onPreviewKeyEvent actionHandled
                    }
                    false
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
                keyActions,
                pointerEventActions,
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
        }
        // Handle pointer input but ensure Tab keys aren't intercepted
        .pointerInput(allKeys, itemKey) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                        PointerEventType.Press -> {
                            requester?.requestFocus()
                            actionHandler.handlePointerEventPress(
                                pointerEvent = event,
                                keybindings = keybindings,
                                selectableLazyListState = selectableState,
                                selectionMode = selectionMode,
                                allKeys = allKeys,
                                key = itemKey,
                            )
                        }
                    }
                }
            }
        }
