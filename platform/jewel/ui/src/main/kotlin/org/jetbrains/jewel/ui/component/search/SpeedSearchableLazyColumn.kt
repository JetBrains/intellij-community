// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.search

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.DefaultMacOsSelectableColumnKeybindings.Companion.isSelectAll
import org.jetbrains.jewel.foundation.lazy.DefaultMacOsSelectableColumnKeybindings.Companion.isSelectNextItem
import org.jetbrains.jewel.foundation.lazy.DefaultMacOsSelectableColumnKeybindings.Companion.isSelectPreviousItem
import org.jetbrains.jewel.foundation.lazy.DefaultSelectableOnKeyEvent.Companion.onSelectAll
import org.jetbrains.jewel.foundation.lazy.DefaultSelectableOnKeyEvent.Companion.onSelectNextItem
import org.jetbrains.jewel.foundation.lazy.DefaultSelectableOnKeyEvent.Companion.onSelectPreviousItem
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
import org.jetbrains.jewel.ui.component.ProvideSearchMatchState
import org.jetbrains.jewel.ui.component.SpeedSearchScope
import org.jetbrains.jewel.ui.component.SpeedSearchState
import org.jetbrains.jewel.ui.component.styling.SearchMatchStyle

/**
 * Creates a selectable lazy column with integrated speed search functionality.
 *
 * This composable combines [SelectableLazyColumn] with speed search capabilities, providing automatic text matching,
 * navigation between matches, and intelligent scrolling behavior. It must be used within a [SpeedSearchScope]
 * (typically provided by [SpeedSearchArea]).
 *
 * **Key features:**
 * - **Automatic matching**: Items are automatically matched against the search query based on their text content
 * - **Smart navigation**: Arrow keys navigate between matching items when search is active
 * - **Auto-scrolling**: Automatically scrolls to keep matching items visible
 * - **Selection integration**: Integrates selection state with search results
 * - **Performance optimized**: Uses background dispatcher for search operations
 *
 * Example usage:
 * ```kotlin
 * SpeedSearchArea {
 *     SpeedSearchableLazyColumn(
 *         modifier = Modifier.focusable()
 *     ) {
 *         items(
 *             items = myList,
 *             textContent = { it.name },
 *             key = { it.id }
 *         ) { item ->
 *             var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
 *             SimpleListItem(
 *                 text = item.name.highlightTextSearch(),
 *                 selected = isSelected,
 *                 active = isActive,
 *                 onTextLayout = { textLayoutResult = it },
 *                 textModifier = Modifier.highlightSpeedSearchMatches(textLayoutResult)
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * @param modifier The modifier to apply to this layout node
 * @param selectionMode The selection mode (single or multiple). Defaults to [SelectionMode.Multiple]
 * @param state The state object controlling selection and scroll position
 * @param contentPadding Padding around the entire list content
 * @param reverseLayout When true, items are laid out in reverse order (bottom to top)
 * @param onSelectedIndexesChange Called when the selected item indexes change
 * @param verticalArrangement The vertical arrangement of items. Defaults to Top (or Bottom if reverseLayout is true)
 * @param horizontalAlignment The horizontal alignment of items
 * @param flingBehavior The fling behavior for scrolling
 * @param keyActions Custom keyboard action handlers. Defaults to [DefaultSelectableLazyColumnKeyActions]
 * @param pointerEventActions Custom pointer event handlers. Defaults to [DefaultSelectableLazyColumnEventAction]
 * @param dispatcher The coroutine dispatcher for search operations. Defaults to [Dispatchers.Default]
 * @param content The DSL content defining the list items. Use [SpeedSearchableLazyColumnScope.items] to add items
 * @see SpeedSearchableLazyColumnScope for available DSL methods
 * @see SelectableLazyColumn for the underlying list component
 * @see org.jetbrains.jewel.ui.component.SpeedSearchArea for the search container
 * @see highlightTextSearch for highlighting search matches
 * @see highlightSpeedSearchMatches for applying match styles
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun SpeedSearchScope.SpeedSearchableLazyColumn(
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
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    content: SpeedSearchableLazyColumnScope.() -> Unit,
) {
    val currentContent = rememberUpdatedState(content)
    val currentStateToList = remember { derivedStateOf { currentContent.value.toList() } }

    val speedSearchKeyActions =
        remember(keyActions) { SpeedSearchableLazyColumnKeyActions(keyActions, speedSearchState) }

    SelectableLazyColumn(
        modifier = modifier.onPreviewKeyEvent(::processKeyEvent),
        selectionMode = selectionMode,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        onSelectedIndexesChange = onSelectedIndexesChange,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        keyActions = speedSearchKeyActions,
        pointerEventActions = pointerEventActions,
        interactionSource = interactionSource,
        content = { SpeedSearchableLazyColumnScopeImpl(speedSearchState, this, searchMatchStyle).content() },
    )

    SpeedSearchableLazyColumnScrollEffect(
        selectableLazyListState = state,
        speedSearchState = speedSearchState,
        keys = currentStateToList.value.second,
        dispatcher = dispatcher,
    )

    LaunchedEffect(state, dispatcher) {
        val entriesState = MutableStateFlow(emptyList<String?>())

        val entriesFlow = snapshotFlow { currentStateToList.value.first }
        async(dispatcher) { entriesFlow.collect(entriesState::emit) }

        speedSearchState.attach(entriesState, dispatcher)
    }
}

internal class SpeedSearchableLazyColumnKeyActions(
    private val delegate: KeyActions,
    private val speedSearchState: SpeedSearchState,
) : KeyActions by delegate {
    override fun handleOnKeyEvent(
        event: KeyEvent,
        keys: List<SelectableLazyListKey>,
        state: SelectableLazyListState,
        selectionMode: SelectionMode,
    ): KeyEvent.() -> Boolean = lambda@{
        val initialIndex = state.lastActiveItemIndex
        if (type != KeyEventType.KeyUp && initialIndex != null) {
            when {
                isSelectAll && selectionMode == SelectionMode.Multiple -> {
                    if (!speedSearchState.isVisible) {
                        onSelectAll(keys, state)
                    }
                    return@lambda true
                }
                isSelectNextItem && speedSearchState.isVisible -> {
                    onSelectNextItem(keys, state, speedSearchState.matchingIndexes.filter { it > initialIndex })
                    return@lambda true
                }
                isSelectPreviousItem && speedSearchState.isVisible -> {
                    onSelectPreviousItem(
                        keys,
                        state,
                        speedSearchState.matchingIndexes.filter { it < initialIndex }.asReversed(),
                    )
                    return@lambda true
                }
                else -> Unit
            }
        }

        delegate.handleOnKeyEvent(event, keys, state, selectionMode).invoke(this)
    }
}

@ExperimentalJewelApi
@ApiStatus.Experimental
public interface SpeedSearchableLazyColumnScope {
    public fun <T : Any> items(
        items: List<T>,
        textContent: (item: T) -> String?,
        key: (item: T) -> Any = { it },
        contentType: (item: T) -> Any? = { it },
        selectable: (item: T) -> Boolean = { true },
        itemContent: @Composable SelectableLazyItemScope.(item: T) -> Unit,
    )

    public fun item(
        key: Any,
        textContent: () -> String?,
        contentType: Any? = null,
        selectable: Boolean = true,
        content: @Composable (SelectableLazyItemScope.() -> Unit),
    )

    public fun stickyHeader(
        key: Any,
        textContent: () -> String?,
        contentType: Any? = null,
        selectable: Boolean = false,
        content: @Composable SelectableLazyItemScope.() -> Unit,
    )
}

internal class SpeedSearchableLazyColumnScopeImpl(
    private val speedSearchState: SpeedSearchState,
    private val delegate: SelectableLazyListScope,
    private val searchMatchStyle: SearchMatchStyle,
) : SpeedSearchableLazyColumnScope {
    override fun <T : Any> items(
        items: List<T>,
        textContent: (item: T) -> String?,
        key: (item: T) -> Any,
        contentType: (item: T) -> Any?,
        selectable: (item: T) -> Boolean,
        itemContent: @Composable (SelectableLazyItemScope.(item: T) -> Unit),
    ) {
        delegate.items(items, key, contentType, selectable) {
            ProvideSearchMatchState(speedSearchState, textContent(it), searchMatchStyle) { itemContent(it) }
        }
    }

    override fun item(
        key: Any,
        textContent: () -> String?,
        contentType: Any?,
        selectable: Boolean,
        content: @Composable (SelectableLazyItemScope.() -> Unit),
    ) {
        delegate.item(key, contentType, selectable) {
            ProvideSearchMatchState(speedSearchState, textContent(), searchMatchStyle) { content() }
        }
    }

    override fun stickyHeader(
        key: Any,
        textContent: () -> String?,
        contentType: Any?,
        selectable: Boolean,
        content: @Composable (SelectableLazyItemScope.() -> Unit),
    ) {
        delegate.stickyHeader(key, contentType, selectable, content)
    }
}

@Composable
internal fun SpeedSearchableLazyColumnScrollEffect(
    selectableLazyListState: SelectableLazyListState,
    speedSearchState: SpeedSearchState,
    keys: List<Any?>,
    dispatcher: CoroutineDispatcher,
) {
    val currentKeys = rememberUpdatedState(keys)

    LaunchedEffect(selectableLazyListState, speedSearchState, dispatcher) {
        val currentSelection = snapshotFlow { selectableLazyListState.selectedKeys }
        val currentKeysValue = snapshotFlow { currentKeys.value }
        val indicesForKeys =
            currentSelection
                .combine(currentKeysValue) { selectedKeys, keys -> keys.indicesForKeys(selectedKeys) to keys }
                .distinctUntilChanged()

        snapshotFlow { speedSearchState.matchingIndexes }
            .distinctUntilChanged()
            .flowOn(dispatcher)
            .combine(indicesForKeys) { indexesMatchingSearchText, (indexesForSelectedKeys, keyValues) ->
                // When search is dismissed or cleared, sync lastActiveItemIndex with selected key position
                if (indexesMatchingSearchText.isEmpty()) {
                    if (indexesForSelectedKeys.isNotEmpty()) {
                        val selectedIndex = indexesForSelectedKeys.first()
                        selectableLazyListState.lastActiveItemIndex = selectedIndex
                    }
                    return@combine
                }

                val visibleItemIndexes = selectableLazyListState.visibleItemsRange

                val matchingSelectionIndex =
                    indexesForSelectedKeys.firstOrNull { indexesMatchingSearchText.binarySearch(it) >= 0 }

                // If any of the selected items match the filter, and it is visible, just skip. But if it's not visible,
                // scroll to it.
                if (matchingSelectionIndex != null) {
                    if (matchingSelectionIndex !in visibleItemIndexes) {
                        selectableLazyListState.scrollToItem(matchingSelectionIndex)
                    }

                    return@combine
                }

                // If any of the visible items match the filter, just select the one closest to any of the selected
                // items
                val indexOfVisibleMatches =
                    visibleItemIndexes.filter { indexesMatchingSearchText.binarySearch(it) >= 0 }

                val bestVisibleMatch =
                    indexOfVisibleMatches
                        .mapNotNull { visibleMatchIndex ->
                            indexesForSelectedKeys
                                .minByOrNull { (visibleMatchIndex - it).absoluteValue }
                                ?.let { visibleMatchIndex to it }
                        }
                        .minByOrNull { (it.first - it.second).absoluteValue }
                        ?.first

                if (bestVisibleMatch != null) {
                    selectableLazyListState.selectedKeys = setOfNotNull(keyValues.getOrNull(bestVisibleMatch))
                    selectableLazyListState.lastActiveItemIndex = bestVisibleMatch
                    return@combine
                }

                // If no items are visible or selected, scroll to the best match after the last visible item
                val indexBeforeVisibleItems = (0 until visibleItemIndexes.first)
                val indexesAfterVisibleItems =
                    (visibleItemIndexes.last until selectableLazyListState.lazyListState.layoutInfo.totalItemsCount)

                // First item after the visible items, or before it (ordered in order)
                val bestMatch =
                    indexesMatchingSearchText.firstOrNull { it in indexesAfterVisibleItems }
                        ?: indexesMatchingSearchText.firstOrNull { it in indexBeforeVisibleItems }

                if (bestMatch != null) {
                    selectableLazyListState.selectedKeys = setOfNotNull(keyValues.getOrNull(bestMatch))
                    selectableLazyListState.scrollToItem(bestMatch)
                }
            }
            .collect()
    }
}

private fun (SpeedSearchableLazyColumnScope.() -> Unit).toList(): Pair<List<String?>, List<Any?>> {
    val texts = mutableListOf<String?>()
    val keys = mutableListOf<Any?>()

    this@toList(
        object : SpeedSearchableLazyColumnScope {
            override fun <T : Any> items(
                items: List<T>,
                textContent: (item: T) -> String?,
                key: (item: T) -> Any,
                contentType: (item: T) -> Any?,
                selectable: (item: T) -> Boolean,
                itemContent: @Composable (SelectableLazyItemScope.(item: T) -> Unit),
            ) {
                items.forEach { item ->
                    texts.add(textContent(item)?.takeIf { selectable(item) })
                    keys.add(key(item))
                }
            }

            override fun item(
                key: Any,
                textContent: () -> String?,
                contentType: Any?,
                selectable: Boolean,
                content: @Composable (SelectableLazyItemScope.() -> Unit),
            ) {
                texts.add(textContent()?.takeIf { selectable })
                keys.add(key)
            }

            override fun stickyHeader(
                key: Any,
                textContent: () -> String?,
                contentType: Any?,
                selectable: Boolean,
                content: @Composable (SelectableLazyItemScope.() -> Unit),
            ) {
                texts.add(textContent()?.takeIf { selectable })
                keys.add(key)
            }
        }
    )

    return (texts to keys)
}

private fun List<Any?>.indicesForKeys(keysToSearch: Collection<Any?>): Set<Int> {
    if (keysToSearch.isEmpty()) return emptySet()

    return buildSet {
        for (index in this@indicesForKeys.indices) {
            val keyForIndex = this@indicesForKeys[index]
            if (keysToSearch.contains(keyForIndex)) {
                add(index)

                // After finding all keys, can stop search
                if (keysToSearch.size == size) break
            }
        }
    }
}
