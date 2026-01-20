// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyItemScope
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListKey
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListScope
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.items
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.DefaultSelectableLazyColumnEventAction
import org.jetbrains.jewel.foundation.lazy.tree.DefaultSelectableLazyColumnKeyActions
import org.jetbrains.jewel.foundation.lazy.tree.KeyActions
import org.jetbrains.jewel.foundation.lazy.tree.PointerEventActions
import org.jetbrains.jewel.foundation.lazy.visibleItemsRange

/**
 * A lazy column with built-in filtering functionality and integrated search input.
 *
 * Combines [SearchArea] with [SelectableLazyColumn] to provide real-time filtering as the user types. Items are
 * filtered using case-insensitive substring matching, and selection is automatically managed to keep valid items
 * selected when the filter changes.
 *
 * @param modifier Modifier to be applied to the outer container.
 * @param lazyColumnModifier Modifier to be applied to the inner [SelectableLazyColumn].
 * @param selectionMode The selection mode for the list.
 * @param state The state holder for the selectable lazy list.
 * @param searchState The state holder for search functionality. Use [rememberSearchAreaState] to create one.
 * @param contentPadding Padding to apply to the list content.
 * @param reverseLayout Whether to reverse the layout direction.
 * @param onSelectedIndexesChange Callback for selection changes. Indexes correspond to the original unfiltered list.
 * @param verticalArrangement Vertical arrangement of items.
 * @param horizontalAlignment Horizontal alignment of items.
 * @param flingBehavior Fling behavior for scrolling.
 * @param keyActions Keyboard actions handler.
 * @param pointerEventActions Pointer event actions handler.
 * @param interactionSource Source of interactions for tracking user interactions.
 * @param dispatcher Coroutine dispatcher for filter computations. Override with test dispatcher for testing.
 * @param content Content builder for the list. Use [FilterableLazyColumnScope] to add filterable items.
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun FilterableLazyColumn(
    modifier: Modifier = Modifier,
    lazyColumnModifier: Modifier = Modifier,
    selectionMode: SelectionMode = SelectionMode.Single,
    state: SelectableLazyListState = rememberSelectableLazyListState(),
    searchState: SearchAreaState = rememberSearchAreaState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    onSelectedIndexesChange: (List<Int>) -> Unit = {},
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    keyActions: KeyActions = DefaultSelectableLazyColumnKeyActions,
    pointerEventActions: PointerEventActions = DefaultSelectableLazyColumnEventAction(),
    interactionSource: MutableInteractionSource? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    content: FilterableLazyColumnScope.() -> Unit,
) {
    val intSource = interactionSource ?: remember { MutableInteractionSource() }

    val container = remember { mutableStateOf(emptyMap<Int, Entry>()) }
    val shouldShowAsError by remember { derivedStateOf { container.value.isEmpty() } }

    val selectableKeys = remember {
        derivedStateOf {
            container.value.values.map {
                if (it.selectable) {
                    SelectableLazyListKey.Selectable(it.key)
                } else {
                    SelectableLazyListKey.NotSelectable(it.key)
                }
            }
        }
    }

    val currentOnSelectedIndexesChange by rememberUpdatedState(onSelectedIndexesChange)
    val currentKeyActions by rememberUpdatedState(keyActions)

    SearchArea(
        state = searchState,
        modifier = modifier,
        interactionSource = intSource,
        error = shouldShowAsError,
        fallbackKeyEventHandler = { event ->
            currentKeyActions.handleOnKeyEvent(event, selectableKeys.value, state, selectionMode).invoke(event)
        },
    ) {
        SelectableLazyColumn(
            modifier = lazyColumnModifier.onPreviewKeyEvent(searchState::processKeyEvent),
            selectionMode = selectionMode,
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            onSelectedIndexesChange = { filteredIndexes ->
                val currentContainer = container.value
                currentOnSelectedIndexesChange(filteredIndexes.mapNotNull { currentContainer[it]?.index })
            },
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            flingBehavior = flingBehavior,
            keyActions = keyActions,
            pointerEventActions = pointerEventActions,
            interactionSource = intSource,
        ) {
            val scope = FilterableLazyColumnScopeImpl(searchState.searchText, this)
            scope.content()
            container.value = scope.entries
        }
    }

    HandleSelectionIfNeeded(
        state = state,
        searchState = searchState,
        dispatcher = dispatcher,
        containerState = { container.value },
    )
}

/**
 * Receiver scope for [FilterableLazyColumn] content.
 *
 * Provides functions to add items with text-based filtering. Items are filtered using case-insensitive substring
 * matching against the search text.
 */
@ExperimentalJewelApi
@ApiStatus.Experimental
public interface FilterableLazyColumnScope {
    /**
     * Adds a list of items to the filterable lazy column.
     *
     * Items are filtered based on case-insensitive substring matching against text from [textContent]. Items with null
     * text content are shown only when search is empty.
     *
     * @param items The list of items to display.
     * @param textContent Function returning searchable text for each item. Return null for non-searchable items.
     * @param key Function returning a unique, stable key for each item.
     * @param contentType Function returning the content type for each item. Used for optimizing lazy composition.
     * @param selectable Function determining whether each item can be selected.
     * @param itemContent The composable content for each item.
     */
    public fun <T : Any> items(
        items: List<T>,
        textContent: (item: T) -> String?,
        key: (item: T) -> Any = { it },
        contentType: (item: T) -> Any? = { it },
        selectable: (item: T) -> Boolean = { true },
        itemContent: @Composable SelectableLazyItemScope.(item: T) -> Unit,
    )

    /**
     * Adds a single item to the filterable lazy column.
     *
     * Filtered based on case-insensitive substring matching against text from [textContent]. Items with null text
     * content are shown only when search is empty.
     *
     * @param key A unique, stable key for the item.
     * @param textContent Function returning searchable text for the item. Return null for non-searchable items.
     * @param contentType The content type for the item. Used for optimizing lazy composition.
     * @param selectable Whether the item can be selected.
     * @param content The composable content for the item.
     */
    public fun item(
        key: Any,
        textContent: () -> String?,
        contentType: Any? = null,
        selectable: Boolean = true,
        content: @Composable (SelectableLazyItemScope.() -> Unit),
    )

    /**
     * Adds a sticky header to the filterable lazy column.
     *
     * Filtered based on case-insensitive substring matching against text from [textContent]. Headers with null text
     * content are shown only when search is empty. Sticky headers remain visible at the top while their content is
     * visible.
     *
     * @param key A unique, stable key for the header.
     * @param textContent Function returning searchable text for the header. Return null for non-searchable headers.
     * @param contentType The content type for the header. Used for optimizing lazy composition.
     * @param selectable Whether the header can be selected.
     * @param content The composable content for the header.
     */
    public fun stickyHeader(
        key: Any,
        textContent: () -> String?,
        contentType: Any? = null,
        selectable: Boolean = false,
        content: @Composable SelectableLazyItemScope.() -> Unit,
    )
}

private class FilterableLazyColumnScopeImpl(
    private val filterText: String,
    private val delegate: SelectableLazyListScope,
) : FilterableLazyColumnScope {
    private var lastIndex: Int = 0
    private val _entries = mutableListOf<Entry>()
    val entries: Map<Int, Entry>
        get() = _entries.associateBy { it.filteredIndex }

    override fun <T : Any> items(
        items: List<T>,
        textContent: (item: T) -> String?,
        key: (item: T) -> Any,
        contentType: (item: T) -> Any?,
        selectable: (item: T) -> Boolean,
        itemContent: @Composable (SelectableLazyItemScope.(item: T) -> Unit),
    ) {
        val filteredList = mutableListOf<T>()

        for (item in items) {
            if (filterText.isEmpty() || textContent(item)?.contains(filterText, ignoreCase = true) ?: false) {
                filteredList.add(item)
                _entries.add(
                    Entry(
                        index = lastIndex++,
                        filteredIndex = _entries.size,
                        selectable = selectable(item),
                        key = key(item),
                    )
                )
            }
        }

        delegate.items(
            items = filteredList,
            key = key,
            contentType = contentType,
            selectable = selectable,
            itemContent = itemContent,
        )
    }

    override fun item(
        key: Any,
        textContent: () -> String?,
        contentType: Any?,
        selectable: Boolean,
        content: @Composable (SelectableLazyItemScope.() -> Unit),
    ) {
        if (filterText.isEmpty() || textContent()?.contains(filterText, ignoreCase = true) ?: false) {
            delegate.item(key, contentType, selectable, content)
            _entries.add(Entry(index = lastIndex++, filteredIndex = _entries.size, selectable = selectable, key = key))
        }
    }

    override fun stickyHeader(
        key: Any,
        textContent: () -> String?,
        contentType: Any?,
        selectable: Boolean,
        content: @Composable (SelectableLazyItemScope.() -> Unit),
    ) {
        if (filterText.isEmpty() || textContent()?.contains(filterText, ignoreCase = true) ?: false) {
            delegate.stickyHeader(key, contentType, selectable, content)
            _entries.add(Entry(index = lastIndex++, filteredIndex = _entries.size, selectable = selectable, key = key))
        }
    }
}

@Composable
private fun HandleSelectionIfNeeded(
    state: SelectableLazyListState,
    searchState: SearchAreaState,
    dispatcher: CoroutineDispatcher,
    containerState: () -> Map<Int, Entry>,
) {
    val currentContainer by rememberUpdatedState(containerState)
    var itemToScrollTo by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        val selectionFlow = snapshotFlow { state.selectedKeys }
        val renderableEntriesFlow = snapshotFlow { currentContainer() }

        combine(selectionFlow, renderableEntriesFlow) { selectedKeys, container ->
                val shouldRefreshSelectionIfNeeded = selectedKeys.isNotEmpty() || searchState.searchText.isNotEmpty()

                val validEntriesByKey = container.values.associateBy { it.key }
                val selectedIndexes = selectedKeys.mapNotNull { validEntriesByKey[it]?.filteredIndex }
                var currentSelectedIndexes = selectedIndexes

                if (shouldRefreshSelectionIfNeeded && selectedIndexes.isEmpty()) {
                    container[state.firstVisibleItemIndex]?.let {
                        state.selectedKeys = setOf(it.key)
                        currentSelectedIndexes = listOf(state.firstVisibleItemIndex)
                    }
                }

                val visibleItems = state.visibleItemsRange
                if (currentSelectedIndexes.isNotEmpty() && currentSelectedIndexes.none { it in visibleItems }) {
                    itemToScrollTo =
                        currentSelectedIndexes.minBy {
                            abs(it - if (it < visibleItems.first) visibleItems.first else visibleItems.last)
                        }
                }
            }
            .flowOn(dispatcher)
            .collect()
    }

    LaunchedEffect(itemToScrollTo) {
        val index = itemToScrollTo ?: return@LaunchedEffect

        state.scrollToItem(index + 2, true)
        itemToScrollTo = null
    }
}

private data class Entry(val index: Int, val filteredIndex: Int, val selectable: Boolean, val key: Any)
