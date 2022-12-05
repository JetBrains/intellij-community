package org.jetbrains.jewel.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.InspectorValueInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.util.visibleItemsRange
import kotlin.math.max
import kotlin.math.min

class FocusableLazyListState internal constructor(internal val listState: LazyListState) : ScrollableState by listState {

    suspend fun focusItem(itemIndex: Int, animateScroll: Boolean = false, scrollOffset: Int = 0) {
        val visibleRange = listState.visibleItemsRange.drop(2).dropLast(4)

        if (itemIndex !in visibleRange && visibleRange.isNotEmpty()) {
            when {
                itemIndex < visibleRange.first() -> listState.scrollToItem(max(0, itemIndex - 2), animateScroll, scrollOffset)
                itemIndex > visibleRange.last() -> {
                    val indexOfFirstVisibleElement = itemIndex - visibleRange.size
                    listState.scrollToItem(min(listState.layoutInfo.totalItemsCount - 1, indexOfFirstVisibleElement - 1), animateScroll, scrollOffset)
                }
            }
        }

        listState.layoutInfo.visibleItemsInfo
            .find { it.index == itemIndex }
            ?.key
            ?.let { it as? FocusableKey }
            ?.focusRequester
            ?.requestFocus()
    }

    internal val lastFocusedIndexState: MutableState<Int?> = mutableStateOf(null)

    val layoutInfo: FocusableLazyListLayoutInfo
        get() = listState.layoutInfo.asFocusable()

    /** The index of the first item that is visible */
    val firstVisibleItemIndex: Int get() = listState.firstVisibleItemIndex

    /**
     * The scroll offset of the first visible item. Scrolling forward is
     * positive - i.e., the amount that the item is offset backwards
     */
    val firstVisibleItemScrollOffset: Int get() = listState.firstVisibleItemScrollOffset

    /**
     * [InteractionSource] that will be used to dispatch drag events when
     * this list is being dragged. If you want to know whether the fling (or
     * animated scroll) is in progress, use [isScrollInProgress].
     */
    val interactionSource: InteractionSource get() = listState.interactionSource

    suspend fun scrollToItem(
        index: Int,
        scrollOffset: Int = 0
    ) = listState.scrollToItem(index, scrollOffset)

    suspend fun animateScrollToItem(
        index: Int,
        scrollOffset: Int = 0
    ) = listState.animateScrollToItem(index, scrollOffset)
}

private fun LazyListLayoutInfo.asFocusable() = object : FocusableLazyListLayoutInfo {
    override val visibleItemsInfo: Sequence<FocusableLazyListItemInfo>
        get() = this@asFocusable.visibleItemsInfo.asFocusable()
    override val viewportStartOffset: Int
        get() = this@asFocusable.viewportStartOffset
    override val viewportEndOffset: Int
        get() = this@asFocusable.viewportEndOffset
    override val totalItemsCount: Int
        get() = this@asFocusable.totalItemsCount
}

private fun List<LazyListItemInfo>.asFocusable(): Sequence<FocusableLazyListItemInfo> = asSequence().map {
    object : FocusableLazyListItemInfo {
        override val index: Int
            get() = it.index
        override val key: FocusableKey
            get() = it.key as FocusableKey
        override val offset: Int
            get() = it.offset
        override val size: Int
            get() = it.size
    }
}

/**
 * Contains useful information about the currently displayed layout state
 * of lazy lists like [LazyColumn] or [LazyRow]. For example you can get
 * the list of currently displayed item.
 *
 * Use [LazyListState.layoutInfo] to retrieve this
 */
interface FocusableLazyListLayoutInfo {

    /**
     * The list of [LazyListItemInfo] representing all the currently visible
     * items.
     */
    val visibleItemsInfo: Sequence<FocusableLazyListItemInfo>

    /**
     * The start offset of the layout's viewport. You can think of it as a
     * minimum offset which would be visible. Usually it is 0, but it can be
     * negative if a content padding was applied as the content displayed in
     * the content padding area is still visible.
     *
     * You can use it to understand what items from [visibleItemsInfo] are
     * fully visible.
     */
    val viewportStartOffset: Int

    /**
     * The end offset of the layout's viewport. You can think of it as a
     * maximum offset which would be visible. Usually it is a size of the lazy
     * list container plus a content padding.
     *
     * You can use it to understand what items from [visibleItemsInfo] are
     * fully visible.
     */
    val viewportEndOffset: Int

    /** The total count of items passed to [LazyColumn] or [LazyRow]. */
    val totalItemsCount: Int
}

/**
 * Contains useful information about an individual item in lazy lists like
 * [LazyColumn] or [LazyRow].
 *
 * @see LazyListLayoutInfo
 */
interface FocusableLazyListItemInfo {

    /** The index of the item in the list. */
    val index: Int

    /** The key of the item which was passed to the item() or items() function. */
    val key: FocusableKey

    /**
     * The main axis offset of the item. It is relative to the start of the
     * lazy list container.
     */
    val offset: Int

    /**
     * The main axis size of the item. Note that if you emit multiple layouts
     * in the composable slot for the item then this size will be calculated as
     * the sum of their sizes.
     */
    val size: Int
}

private suspend fun LazyListState.scrollToItem(index: Int, animate: Boolean, scrollOffset: Int = 0) =
    if (animate) animateScrollToItem(index, scrollOffset) else scrollToItem(index, scrollOffset)

class FocusableKey(val focusRequester: FocusRequester, val key: Any?)

fun FocusableLazyListState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0
) = FocusableLazyListState(
    listState = LazyListState(
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset
    )
)

@Composable
fun FocusableLazyColumn(
    modifier: Modifier = Modifier,
    state: FocusableLazyListState = rememberFocusableLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    onKeyPressed: (KeyEvent, Int) -> Boolean = { _, _ -> false },
    content: LazyListScope.() -> Unit
) {
    Box(
        modifier
            .onPreviewKeyEvent { event ->
                state.lastFocusedIndexState.value?.let { onKeyPressed(event, it) } ?: false
            }
            .onFocusChanged {
                println(it)
                if (!it.hasFocus) state.lastFocusedIndexState.value = null
            }
            .focusable()
    ) {
        LazyColumn(
            state = state.listState,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            flingBehavior = flingBehavior
        ) {
            LazyListScopeContainer().apply(content)
                .entries
                .forEach { entry ->
                    when (entry) {
                        is LazyListScopeContainer.Entry.Item ->
                            item(entry) { index: Int -> state.lastFocusedIndexState.value = index }
                        is LazyListScopeContainer.Entry.Items ->
                            items(entry) { index: Int -> state.lastFocusedIndexState.value = index }
                        is LazyListScopeContainer.Entry.StickyHeader ->
                            stickyHeader(entry) { index: Int -> state.lastFocusedIndexState.value = index }
                    }
                }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.stickyHeader(
    entry: LazyListScopeContainer.Entry.StickyHeader,
    onItemFocused: (Int) -> Unit
) {
    val fr = FocusRequester()
    stickyHeader(FocusableKey(fr, entry.key)) {
        BoxWithConstraints(
            Modifier
                .focusRequester(fr)
                .onFocusChanged { if (it.hasFocus) onItemFocused(entry.innerIndex) }
                .focusable()
                .clickable(onClick = { fr.requestFocus() })
        ) {
            entry.content(LazyItemScope())
        }
    }
}

private fun LazyListScope.items(
    entry: LazyListScopeContainer.Entry.Items,
    onItemFocused: (Int) -> Unit
) {
    val requesters = List(entry.count) { FocusRequester() }
    items(count = entry.count, key = { FocusableKey(requesters[it], entry.key?.invoke(it)) }) { itemIndex ->
        BoxWithConstraints(
            Modifier
                .focusRequester(requesters[entry.innerIndex + itemIndex])
                .onFocusChanged { if (it.hasFocus) onItemFocused(entry.innerIndex + itemIndex) }
                .focusable()
                .clickable(onClick = { requesters[entry.innerIndex + itemIndex].requestFocus() })
        ) {
            entry.itemContent(LazyItemScope(), itemIndex)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.item(
    entry: LazyListScopeContainer.Entry.Item,
    onItemFocused: (Int) -> Unit
) {
    val fr = FocusRequester()
    item(FocusableKey(fr, entry.key)) {
        BoxWithConstraints(
            Modifier
                .focusRequester(fr)
                .onFocusChanged { if (it.hasFocus) onItemFocused(entry.innerIndex) }
                .focusable()
                .combinedClickable(onClick = { fr.requestFocus() })
        ) {
            entry.content(LazyItemScope())
        }
    }
}

internal class LazyListScopeContainer : LazyListScope {

    private var lastIndex = 0

    internal sealed class Entry {

        data class Item(val key: Any?, val innerIndex: Int, val contentType: Any?, val content: @Composable LazyItemScope.() -> Unit) : Entry()

        data class Items(
            val count: Int,
            val key: ((index: Int) -> Any)?,
            val contentType: (index: Int) -> Any?,
            val innerIndex: Int,
            val itemContent: @Composable LazyItemScope.(index: Int) -> Unit
        ) : Entry()

        data class StickyHeader(
            val key: Any?,
            val innerIndex: Int,
            val contentType: Any?,
            val content: @Composable LazyItemScope.() -> Unit
        ) : Entry()
    }

    internal val entries = mutableListOf<Entry>()

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        entries.add(Entry.Item(key, lastIndex++, contentType, content))
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit
    ) {
        entries.add(Entry.Items(count, key, contentType, lastIndex, itemContent))
        lastIndex += count
    }

    @ExperimentalFoundationApi
    override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        entries.add(Entry.StickyHeader(key, lastIndex++, contentType, content))
    }
}

@Composable
fun rememberFocusableLazyListState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0
) = FocusableLazyListState(rememberLazyListState(firstVisibleItemIndex, firstVisibleItemScrollOffset))

@Composable
fun BoxWithConstraintsScope.LazyItemScope(): LazyItemScope =
    LazyItemScopeImpl(LocalDensity.current, constraints)

internal data class LazyItemScopeImpl(
    val density: Density,
    val constraints: Constraints
) : LazyItemScope {

    private val maxWidth: Dp = with(density) { constraints.maxWidth.toDp() }
    private val maxHeight: Dp = with(density) { constraints.maxHeight.toDp() }

    override fun Modifier.fillParentMaxSize(fraction: Float) = size(
        maxWidth * fraction,
        maxHeight * fraction
    )

    override fun Modifier.fillParentMaxWidth(fraction: Float) =
        width(maxWidth * fraction)

    override fun Modifier.fillParentMaxHeight(fraction: Float) =
        height(maxHeight * fraction)

    @ExperimentalFoundationApi
    override fun Modifier.animateItemPlacement(animationSpec: FiniteAnimationSpec<IntOffset>) =
        this.then(AnimateItemPlacementModifier(animationSpec, debugInspectorInfo {
            name = "animateItemPlacement"
            value = animationSpec
        }))
}

private class AnimateItemPlacementModifier(
    val animationSpec: FiniteAnimationSpec<IntOffset>,
    inspectorInfo: InspectorInfo.() -> Unit,
) : ParentDataModifier, InspectorValueInfo(inspectorInfo) {

    override fun Density.modifyParentData(parentData: Any?): Any = animationSpec

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnimateItemPlacementModifier) return false
        return animationSpec != other.animationSpec
    }

    override fun hashCode(): Int {
        return animationSpec.hashCode()
    }
}
