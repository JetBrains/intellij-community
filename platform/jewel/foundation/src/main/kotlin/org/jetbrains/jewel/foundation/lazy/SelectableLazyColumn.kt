package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
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
    onSelectedIndexesChanged: (List<Int>) -> Unit = {},
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    keyActions: KeyActions = DefaultSelectableLazyColumnKeyActions,
    pointerEventActions: PointerEventActions = DefaultSelectableLazyColumnEventAction(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: SelectableLazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val container = SelectableLazyListScopeContainer().apply(content)

    val keys = remember(container) { container.getKeys() }
    var isFocused by remember { mutableStateOf(false) }

    val latestOnSelectedIndexesChanged = rememberUpdatedState(onSelectedIndexesChanged)
    LaunchedEffect(state, container) {
        snapshotFlow { state.selectedKeys }
            .collect { selectedKeys ->
                val indices = selectedKeys.mapNotNull { key -> container.getKeyIndex(key) }
                latestOnSelectedIndexesChanged.value.invoke(indices)
            }
    }
    val focusManager = LocalFocusManager.current
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
                .focusRequester(focusRequester)
                .focusable(interactionSource = interactionSource)
                .onPreviewKeyEvent { event ->
                    // Handle Tab key press to move focus to next item
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
                        val focusDirection = if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
                        focusManager.moveFocus(focusDirection)
                        return@onPreviewKeyEvent true
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
    pointerInput(allKeys, itemKey) {
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
