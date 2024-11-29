package org.jetbrains.jewel.foundation.lazy

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.max

@Suppress("unused")
public val LazyListState.visibleItemsRange: IntRange
    get() = firstVisibleItemIndex..firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size

public val SelectableLazyListState.visibleItemsRange: IntRange
    get() = firstVisibleItemIndex..firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size

public interface SelectableScope {
    public var selectedKeys: Set<Any>
}

/**
 * State object for a selectable lazy list, which extends [ScrollableState].
 *
 * @param lazyListState The state object for the underlying lazy list.
 */
public class SelectableLazyListState(public val lazyListState: LazyListState) :
    ScrollableState by lazyListState, SelectableScope {
    internal var lastKeyEventUsedMouse: Boolean = false

    override var selectedKeys: Set<Any> by mutableStateOf(emptySet())

    public var lastActiveItemIndex: Int? = null

    /**
     * @param itemIndex The index of the item to focus on.
     * @param animateScroll Whether to animate the scroll to the focused item.
     * @param scrollOffset The scroll offset for the focused item.
     */
    public suspend fun scrollToItem(itemIndex: Int, animateScroll: Boolean = false, scrollOffset: Int = 0) {
        val visibleRange = visibleItemsRange.drop(2).dropLast(4)
        if (itemIndex !in visibleRange && visibleRange.isNotEmpty()) {
            when {
                itemIndex < visibleRange.first() ->
                    lazyListState.scrollToItem(max(0, itemIndex - 2), animateScroll, scrollOffset)

                itemIndex > visibleRange.last() ->
                    lazyListState.scrollToItem(
                        index = max(itemIndex - (visibleRange.size + 2), 0),
                        animate = animateScroll,
                        scrollOffset = 0,
                    )
            }
        }
        lastActiveItemIndex = itemIndex
    }

    public val layoutInfo: LazyListLayoutInfo
        get() = lazyListState.layoutInfo

    /** The index of the first item that is visible. */
    public val firstVisibleItemIndex: Int
        get() = lazyListState.firstVisibleItemIndex

    /**
     * The scroll offset of the first visible item. Scrolling forward is positive - i.e., the amount that the item is
     * offset backwards.
     */
    @Suppress("unused")
    public val firstVisibleItemScrollOffset: Int
        get() = lazyListState.firstVisibleItemScrollOffset

    /**
     * [InteractionSource] that will be used to dispatch drag events when this list is being dragged. If you want to
     * know whether the fling (or animated scroll) is in progress, use [isScrollInProgress].
     */
    public val interactionSource: InteractionSource
        get() = lazyListState.interactionSource
}

private suspend fun LazyListState.scrollToItem(index: Int, animate: Boolean, scrollOffset: Int = 0) {
    if (animate) {
        animateScrollToItem(index, scrollOffset)
    } else {
        scrollToItem(index, scrollOffset)
    }
}

/** Represents a selectable key used in a selectable lazy list. */
public sealed class SelectableLazyListKey {
    /** The key associated with the item. */
    public abstract val key: Any

    /**
     * Represents a selectable item key.
     *
     * @param key The key associated with the item.
     */
    public class Selectable(override val key: Any) : SelectableLazyListKey()

    /**
     * Represents a non-selectable item key.
     *
     * @param key The key associated with the item.
     */
    public class NotSelectable(override val key: Any) : SelectableLazyListKey()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SelectableLazyListKey

        return key == other.key
    }

    override fun hashCode(): Int = key.hashCode()
}

public interface SelectableLazyItemScope : LazyItemScope {
    public val isSelected: Boolean
    public val isActive: Boolean
}

/** Specifies the selection mode for a selectable lazy list. */
public enum class SelectionMode {
    /** No selection is allowed. */
    None,

    /** Only a single item can be selected. */
    Single,

    /** Multiple items can be selected. */
    Multiple,
}

/**
 * Remembers the state of a selectable lazy list.
 *
 * @param firstVisibleItemIndex The index of the first visible item.
 * @param firstVisibleItemScrollOffset The scroll offset of the first visible item.
 * @return The remembered state of the selectable lazy list.
 */
@Composable
public fun rememberSelectableLazyListState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0,
): SelectableLazyListState = remember {
    SelectableLazyListState(LazyListState(firstVisibleItemIndex, firstVisibleItemScrollOffset))
}
