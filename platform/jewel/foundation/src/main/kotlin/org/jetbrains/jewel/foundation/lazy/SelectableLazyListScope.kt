package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.jewel.foundation.tree.PointerEventScopedActions
import org.jetbrains.jewel.foundation.utils.Log

/**
 * Interface defining the scope for building a selectable lazy list.
 */
interface SelectableLazyListScope {

    /**
     * Adds an item to the selectable lazy list.
     *
     * @param key The key that uniquely identifies the item.
     * @param contentType The content type of the item.
     * @param focusable Whether the item is focusable or not.
     * @param selectable Whether the item is selectable or not.
     * @param content The content of the item, specified as a lambda function with a [SelectableLazyItemScope] receiver.
     */
    fun item(
        key: Any,
        contentType: Any? = null,
        focusable: Boolean = true,
        selectable: Boolean = true,
        content: @Composable SelectableLazyItemScope.() -> Unit
    )

    /**
     * Adds multiple items to the selectable lazy list.
     *
     * @param count The number of items to add.
     * @param key A lambda function that provides the key for each item based on the index.
     * @param contentType A lambda function that provides the content type for each item based on the index.
     * @param focusable A lambda function that determines whether each item is focusable based on the index.
     * @param selectable A lambda function that determines whether each item is selectable based on the index.
     * @param itemContent The content of each item, specified as a lambda function with a [SelectableLazyItemScope] receiver and an index parameter.
     */
    fun items(
        count: Int,
        key: (index: Int) -> Any,
        contentType: (index: Int) -> Any? = { null },
        focusable: (index: Int) -> Boolean = { true },
        selectable: (index: Int) -> Boolean = { true },
        itemContent: @Composable SelectableLazyItemScope.(index: Int) -> Unit
    )

    /**
     * Adds a sticky header to the selectable lazy list.
     *
     * @param key The key that uniquely identifies the sticky header.
     * @param contentType The content type of the sticky header.
     * @param focusable Whether the sticky header is focusable or not.
     * @param selectable Whether the sticky header is selectable or not.
     * @param content The content of the sticky header, specified as a lambda function with a [SelectableLazyItemScope] receiver.
     */
    fun stickyHeader(
        key: Any,
        contentType: Any? = null,
        focusable: Boolean = false,
        selectable: Boolean = false,
        content: @Composable SelectableLazyItemScope.() -> Unit
    )
}

internal class SelectableLazyListScopeContainer(
    private val delegate: LazyListScope,
    private val state: SelectableLazyListState,
    private val pointerEventScopedActions: PointerEventScopedActions
) : SelectableLazyListScope {

    @Composable
    private fun Modifier.focusable(key: SelectableKey.Focusable, isFocused: Boolean) =
        focusRequester(key.focusRequester)
            .onFocusChanged {
                if (it.hasFocus) {
                    state.lastFocusedKeyState.value = SelectableLazyListState.LastFocusedKeyContainer.Set(key)
                    state.lastFocusedIndexState.value = state.keys.indexOf(key)
                }
            }
            .focusable()
            .pointerInput(isFocused) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(false)
                        if (!isFocused) key.focusRequester.requestFocus()
                        Log.d("focus requested on single item-> ${state.keys.indexOf(key)}")
                    }
                }
            }

    @Composable
    private fun Modifier.selectable(selectableKey: SelectableKey, scope: CoroutineScope = rememberCoroutineScope()) =
        onPointerEvent(PointerEventType.Press) {
            pointerEventScopedActions.handlePointerEventPress(it, state.keybindings, scope, selectableKey.key)
        }

    override fun item(
        key: Any,
        contentType: Any?,
        focusable: Boolean,
        selectable: Boolean,
        content: @Composable SelectableLazyItemScope.() -> Unit
    ) {
        val focusRequester = FocusRequester()
        val selectableKey = if (focusable) {
            SelectableKey.Focusable(focusRequester, key, selectable)
        } else {
            SelectableKey.NotFocusable(key, selectable)
        }
        state.attachKey(selectableKey)
        delegate.item(selectableKey, contentType) {
            singleItem(selectableKey, key, selectable, focusable, content)
        }
    }

    @Composable
    private fun LazyItemScope.singleItem(
        selectableKey: SelectableKey,
        key: Any,
        selectable: Boolean,
        focusable: Boolean,
        content: @Composable (SelectableLazyItemScope.() -> Unit)
    ) {
        // should be like that, but it seems does not work,
        // it do not recompose when state.lastFocusedIndex == state.keys.indexOf(selectableKey) changes
        // val isFocused by remember {
        // derivedStateOf(structuralEqualityPolicy()) { state.lastFocusedIndex == state.keys.indexOf(selectableKey) }
        // }
        val isFocused = state.lastFocusedIndex == state.keys.indexOf(selectableKey)
        val isSelected = key in state.selectedIdsMap
        val scope = rememberCoroutineScope()
        Box(
            Modifier
                .then(if (selectable) Modifier.selectable(selectableKey, scope) else Modifier)
                .then(if (focusable) Modifier.focusable(selectableKey as SelectableKey.Focusable, isFocused) else Modifier)
        ) {
            content(SelectableLazyItemScope(isSelected, isFocused))
        }
    }

    override fun items(
        count: Int,
        key: (index: Int) -> Any,
        contentType: (index: Int) -> Any?,
        focusable: (index: Int) -> Boolean,
        selectable: (index: Int) -> Boolean,
        itemContent: @Composable SelectableLazyItemScope.(index: Int) -> Unit
    ) {
        val totalItems = state.keys.size
        val selectableKeys: List<SelectableKey> = List(count) {
            if (focusable(it)) {
                SelectableKey.Focusable(FocusRequester(), key(it), selectable(it))
            } else {
                SelectableKey.NotFocusable(
                    key(it),
                    selectable(it)
                )
            }
        }
        state.attachKeys(selectableKeys)
        Log.w("there are ${state.keys.size} keys")
        Log.w(state.keys.map { it.key }.joinToString("\n"))
        delegate.items(
            count = count,
            key = { selectableKeys[it] },
            itemContent = { index ->
                if (selectableKeys[index] in state.selectedIdsMap) Log.e("i'm the element with index $index and i'm selected! ")
                val isFocused = state.lastFocusedIndex == totalItems + index
                val isSelected = selectableKeys[index] in state.selectedIdsMap
                Box(
                    Modifier
                        .then(if (selectable(index)) Modifier.selectable(selectableKeys[index]) else Modifier)
                        .then(if (focusable(index)) Modifier.focusable(selectableKeys[index] as SelectableKey.Focusable, isFocused) else Modifier)
                ) {
                    itemContent(SelectableLazyItemScope(isFocused, isSelected), index)
                }
            }
        )
    }

    @ExperimentalFoundationApi
    override fun stickyHeader(
        key: Any,
        contentType: Any?,
        focusable: Boolean,
        selectable: Boolean,
        content: @Composable SelectableLazyItemScope.() -> Unit
    ) {
        val focusRequester = FocusRequester()
        val selectableKey = if (focusable) {
            SelectableKey.Focusable(focusRequester, key, selectable)
        } else {
            SelectableKey.NotFocusable(key, selectable)
        }
        state.attachKey(selectableKey)
        delegate.stickyHeader(selectableKey, contentType) {
            singleItem(selectableKey, key, selectable, focusable, content)
        }
    }
}

@Composable
fun LazyItemScope.SelectableLazyItemScope(isFocused: Boolean = false, isSelected: Boolean = false): SelectableLazyItemScope =
    SelectableLazyItemScopeDelegate(this, isFocused, isSelected)

internal class SelectableLazyItemScopeDelegate(
    private val delegate: LazyItemScope,
    override val isFocused: Boolean,
    override val isSelected: Boolean
) : SelectableLazyItemScope, LazyItemScope by delegate
