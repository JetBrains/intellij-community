// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.search

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyItemScope
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
import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.SpeedSearchItemScope
import org.jetbrains.jewel.ui.component.SpeedSearchState
import org.jetbrains.jewel.ui.component.styling.SearchMatchStyle
import org.jetbrains.jewel.ui.component.styling.SpeedSearchStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.theme.searchMatchStyle
import org.jetbrains.jewel.ui.theme.speedSearchStyle
import org.jetbrains.jewel.ui.theme.textFieldStyle

@Composable
public fun SpeedSearchSelectableLazyColumn(
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
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    styling: SpeedSearchStyle = JewelTheme.speedSearchStyle,
    textFieldStyle: TextFieldStyle = JewelTheme.textFieldStyle,
    searchMatchStyle: SearchMatchStyle = JewelTheme.searchMatchStyle,
    buildMatcher: (String) -> SpeedSearchMatcher = SpeedSearchMatcher::patternMatcher,
    content: SpeedSearchSelectableListScope.() -> Unit,
) {
    val speedSearchState = rememberSpeedSearchState(content, buildMatcher)

    SpeedSearchArea(
        speedSearchState,
        modifier,
        styling,
        textFieldStyle,
        textStyle,
        searchMatchStyle,
        interactionSource,
    ) {
        SelectableLazyColumn(
            modifier = Modifier.onPreviewKeyEvent(::processKeyEvent),
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
            interactionSource = interactionSource ?: this.interactionSource,
            content = { SpeedSearchSelectableListScopeImpl(speedSearchState, this, searchMatchStyle).content() },
        )
    }

    SelectableLazyListAutoScroll(state, speedSearchState)

    LaunchedEffect(state) { speedSearchState.attach() }
}

public interface SpeedSearchSelectableListScope {
    public fun <T : Any> items(
        items: List<T>,
        textContent: (item: T) -> String?,
        key: (item: T) -> Any = { it },
        contentType: (item: T) -> Any? = { it },
        selectable: (item: T) -> Boolean = { true },
        itemContent: @Composable SpeedSearchSelectableLazyItemScope.(item: T) -> Unit,
    )

    public fun item(
        key: Any,
        textContent: () -> String?,
        contentType: Any? = null,
        selectable: Boolean = true,
        content: @Composable (SpeedSearchSelectableLazyItemScope.() -> Unit),
    )

    public fun stickyHeader(
        key: Any,
        textContent: () -> String?,
        contentType: Any? = null,
        selectable: Boolean = false,
        content: @Composable SpeedSearchSelectableLazyItemScope.() -> Unit,
    )
}

public interface SpeedSearchSelectableLazyItemScope : SelectableLazyItemScope, SpeedSearchItemScope

internal class SpeedSearchSelectableListScopeImpl(
    private val speedSearchState: SpeedSearchState,
    private val delegate: SelectableLazyListScope,
    private val searchMatchStyle: SearchMatchStyle,
) : SpeedSearchSelectableListScope {
    override fun <T : Any> items(
        items: List<T>,
        textContent: (item: T) -> String?,
        key: (item: T) -> Any,
        contentType: (item: T) -> Any?,
        selectable: (item: T) -> Boolean,
        itemContent: @Composable (SpeedSearchSelectableLazyItemScope.(item: T) -> Unit),
    ) {
        delegate.items(items, key, contentType, selectable) {
            SpeedSearchSelectableLazyItemScope(speedSearchState, textContent(it), searchMatchStyle).itemContent(it)
        }
    }

    override fun item(
        key: Any,
        textContent: () -> String?,
        contentType: Any?,
        selectable: Boolean,
        content: @Composable (SpeedSearchSelectableLazyItemScope.() -> Unit),
    ) {
        delegate.item(key, contentType, selectable) {
            SpeedSearchSelectableLazyItemScope(speedSearchState, textContent(), searchMatchStyle).content()
        }
    }

    override fun stickyHeader(
        key: Any,
        textContent: () -> String?,
        contentType: Any?,
        selectable: Boolean,
        content: @Composable (SpeedSearchSelectableLazyItemScope.() -> Unit),
    ) {
        delegate.stickyHeader(key, contentType, selectable) {
            SpeedSearchSelectableLazyItemScope(speedSearchState, textContent(), searchMatchStyle).content()
        }
    }
}

@Composable
private fun SelectableLazyItemScope.SpeedSearchSelectableLazyItemScope(
    state: SpeedSearchState,
    textContent: String?,
    style: SearchMatchStyle,
) =
    object : SpeedSearchSelectableLazyItemScope, SelectableLazyItemScope by this {
        override val matches: List<IntRange>?
            get() = state.matchingParts(textContent)

        override fun Modifier.highlightTextSearchArea(textLayoutResult: TextLayoutResult?): Modifier =
            highlightTextSearchArea(textLayoutResult, style, matches)

        override fun CharSequence.highlightTextSearch(): AnnotatedString = highlightTextSearch(style, matches)
    }

@Composable
private fun rememberSpeedSearchState(
    content: SpeedSearchSelectableListScope.() -> Unit,
    buildMatcher: (String) -> SpeedSearchMatcher,
): SelectableSpeedSearchState {
    val currentMatcherBuilder = rememberUpdatedState(buildMatcher)

    val currentState = rememberUpdatedState(content)
    val entries = remember { derivedStateOf { currentState.value.toList() } }

    return remember { SelectableSpeedSearchState(entries, currentMatcherBuilder) }
}

@Composable
private fun SelectableLazyListAutoScroll(
    selectableLazyListState: SelectableLazyListState,
    speedSearchState: SelectableSpeedSearchState,
) {
    LaunchedEffect(Unit) {
        snapshotFlow { speedSearchState.matchingIndexes }
            .filter { it.isNotEmpty() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .onEach { matchingIndexes ->
                val visibleItemIndexes = selectableLazyListState.visibleItemsRange

                val matchingSelectionIndex =
                    selectableLazyListState.selectedKeys
                        .let { speedSearchState.keysToIndex(it) }
                        .firstOrNull { matchingIndexes.binarySearch(it) >= 0 }

                // If any of the selected items match the filter, and it is visible, just skip. But if it's not visible,
                // scroll to it.
                if (matchingSelectionIndex != null) {
                    if (matchingSelectionIndex !in visibleItemIndexes) {
                        selectableLazyListState.scrollToItem(matchingSelectionIndex)
                    }

                    return@onEach
                }

                // If any of the visible items match the filter, just select it, no need to scroll
                val indexOfVisibleMatch = visibleItemIndexes.firstOrNull { matchingIndexes.binarySearch(it) >= 0 }
                if (indexOfVisibleMatch != null) {
                    selectableLazyListState.selectedKeys =
                        setOfNotNull(speedSearchState.indexToKey(indexOfVisibleMatch))
                    return@onEach
                }

                // If no items are visible or selected, scroll to the closest match
                val middleVisible = (visibleItemIndexes.first + visibleItemIndexes.last) / 2
                val itemToFocus = matchingIndexes.minBy { (middleVisible - it).absoluteValue }

                selectableLazyListState.selectedKeys = setOfNotNull(speedSearchState.indexToKey(itemToFocus))
                selectableLazyListState.scrollToItem(itemToFocus)
            }
            .collect()
    }
}

private class SelectableSpeedSearchState(
    private val listState: State<Pair<List<String?>, List<Any?>>>,
    private val matcherBuilderState: State<(String) -> SpeedSearchMatcher>,
) : SpeedSearchState() {
    private var matches: Map<String?, List<IntRange>> by mutableStateOf(emptyMap())
    override var hasMatches: Boolean by mutableStateOf(true)
        private set

    var matchingIndexes by mutableStateOf(emptyList<Int>())
        private set

    fun keysToIndex(keysToSearch: Set<Any?>): Set<Int> {
        if (keysToSearch.isEmpty()) return emptySet()

        return buildSet {
            val keys = listState.value.second
            for (index in keys.indices) {
                val keyForIndex = keys[index]
                if (keysToSearch.contains(keyForIndex)) {
                    add(index)

                    // After finding all keys, can stop search
                    if (keysToSearch.size == size) break
                }
            }
        }
    }

    fun indexToKey(index: Int): Any? = listState.value.second.getOrNull(index)

    override fun matchingParts(text: String?): List<IntRange>? = matches[text]

    // Using only 'derivedStates' caused a lot of recompositions and caused a rendering lag.
    // To prevent this issue, I'm aggregating the states in this method and posting the values
    // to the relevant properties.
    suspend fun attach() {
        val searchTextFlow = snapshotFlow { searchText }
        val entries = snapshotFlow { listState.value.first }
        val matcher = snapshotFlow { matcherBuilderState.value }

        combine(searchTextFlow, entries, matcher) { text, items, buildMatcher ->
                if (text.isBlank()) {
                    matches = emptyMap()
                    matchingIndexes = emptyList()
                    hasMatches = true
                    return@combine
                }

                val matcher = buildMatcher(text)

                // Please note that use the default capacity can have a significant impact on performance for larger
                // data sets. After the first "round", we can start creating the array with an "educated guess" to
                // prevent tons of array copy in memory
                val newMatchingIndexes = ArrayList<Int>(matchingIndexes.size.takeIf { it > 0 } ?: 128)
                val newMatches = hashMapOf<String?, List<IntRange>>()
                var anyMatch = false

                for (index in items.indices) {
                    val item = items[index]
                    val matches = matcher.matches(item)

                    if (matches is SpeedSearchMatcher.MatchResult.Match) {
                        newMatchingIndexes.add(index)
                        newMatches[item] = matches.ranges
                        anyMatch = true
                    }
                }

                newMatchingIndexes.trimToSize()

                matches = newMatches
                matchingIndexes = newMatchingIndexes
                hasMatches = anyMatch
            }
            .flowOn(Dispatchers.Default)
            .collect()
    }
}

private fun (SpeedSearchSelectableListScope.() -> Unit).toList(): Pair<List<String?>, List<Any?>> {
    val texts = mutableListOf<String?>()
    val keys = mutableListOf<Any?>()

    this@toList(
        object : SpeedSearchSelectableListScope {
            override fun <T : Any> items(
                items: List<T>,
                textContent: (item: T) -> String?,
                key: (item: T) -> Any,
                contentType: (item: T) -> Any?,
                selectable: (item: T) -> Boolean,
                itemContent: @Composable (SpeedSearchSelectableLazyItemScope.(item: T) -> Unit),
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
                content: @Composable (SpeedSearchSelectableLazyItemScope.() -> Unit),
            ) {
                texts.add(textContent()?.takeIf { selectable })
                keys.add(key)
            }

            override fun stickyHeader(
                key: Any,
                textContent: () -> String?,
                contentType: Any?,
                selectable: Boolean,
                content: @Composable (SpeedSearchSelectableLazyItemScope.() -> Unit),
            ) {
                texts.add(textContent()?.takeIf { selectable })
                keys.add(key)
            }
        }
    )

    return (texts to keys)
}
