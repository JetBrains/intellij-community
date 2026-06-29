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
import org.jetbrains.jewel.foundation.util.JewelLogger

private val logger = JewelLogger.getInstance(SelectableLazyListState::class.java)

/** The index range of currently visible items in this [LazyListState]. */
@Suppress("unused")
public val LazyListState.visibleItemsRange: IntRange
    get() = firstVisibleItemIndex until firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size

/** The index range of currently visible items in this [SelectableLazyListState]. */
public val SelectableLazyListState.visibleItemsRange: IntRange
    get() = firstVisibleItemIndex until firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size

/** Exposes mutable [selectedKeys] to composables and state holders managing a selectable list. */
public interface SelectableScope {
    /** The set of keys currently selected in the list. */
    public var selectedKeys: Set<Any>
}

/**
 * Legacy Selectable list state used for backward compatibility.
 *
 * New code should prefer [SingleSelectionLazyListState] or [MultiSelectionLazyListState] together with
 * [SingleSelectionLazyColumn] or [MultiSelectionLazyColumn].
 *
 * Pre-selecting keys does **not** trigger [SelectableLazyColumn]'s `onSelectedIndexesChange` on the first composition,
 * because callback emission is seeded from initial selection and emitted only on later changes.
 *
 * @param lazyListState Underlying [LazyListState] used for scroll/layout state.
 * @param selectionMode Legacy selection mode metadata kept for API compatibility and legacy diagnostics.
 * @param initialSelectedKeys Initially selected keys assigned to [selectedKeys] at construction time. This constructor
 *   does not normalize keys against [selectionMode]. When this state is constructed manually, keys should be normalized
 *   for the chosen mode (for example, at most one key for [SelectionMode.Single], and no keys for
 *   [SelectionMode.None]). [rememberSelectableLazyListState] provides built-in legacy normalization from a [List].
 */
public class SelectableLazyListState
public constructor(
    public val lazyListState: LazyListState,
    public val selectionMode: SelectionMode = SelectionMode.Multiple,
    initialSelectedKeys: Set<Any> = emptySet(),
) : ScrollableState by lazyListState, SelectableScope {
    /**
     * Compatibility overload retained for binary compatibility only. Use the primary constructor with [selectionMode]
     * and [initialSelectedKeys] instead.
     */
    @Deprecated(
        message = "Use SelectableLazyListState(lazyListState, selectionMode, initialSelectedKeys) instead.",
        level = DeprecationLevel.HIDDEN,
    )
    public constructor(lazyListState: LazyListState) : this(lazyListState, SelectionMode.Multiple, emptySet())

    internal var lastKeyEventUsedMouse: Boolean = false

    /** Flag indicating whether the user is currently navigating via keyboard */
    public var isKeyboardNavigating: Boolean by mutableStateOf(false)

    /** The set of keys currently selected in the list. */
    override var selectedKeys: Set<Any> by mutableStateOf(initialSelectedKeys)

    /** The index of the last item that was made active, or null if no item has been activated yet. */
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

    /** Layout information about the currently visible items and overall list state. */
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

/**
 * Type-safe state holder for single-selection lazy lists, enforcing [SelectionMode.Single] semantics. Delegates scroll
 * and layout to an underlying [SelectableLazyListState].
 */
public class SingleSelectionLazyListState internal constructor(internal val delegate: SelectableLazyListState) :
    ScrollableState by delegate, SelectableScope {
    /**
     * Type-safe state holder for single-selection lazy lists.
     *
     * This state guarantees single-selection semantics:
     * - [selectionMode] is always [SelectionMode.Single].
     * - assigning multiple keys to [selectedKeys] keeps only the first key.
     *
     * Use with [SingleSelectionLazyColumn].
     *
     * @param lazyListState Underlying [LazyListState] used for scroll/layout state.
     * @param initialSelectedKey Optional initial selected key applied once at creation.
     */
    public constructor(
        lazyListState: LazyListState,
        initialSelectedKey: Any? = null,
    ) : this(
        SelectableLazyListState(
            lazyListState = lazyListState,
            selectionMode = SelectionMode.Single,
            initialSelectedKeys = initialSelectedKey?.let(::setOf).orEmpty(),
        )
    )

    /** The underlying [LazyListState] used for scroll and layout. */
    public val lazyListState: LazyListState
        get() = delegate.lazyListState

    /** The selection mode, always [SelectionMode.Single] for this state. */
    public val selectionMode: SelectionMode
        get() = SelectionMode.Single

    /** The set of keys currently selected; enforces at most one key. */
    override var selectedKeys: Set<Any>
        get() = delegate.selectedKeys
        set(value) {
            if (value.size > 1) {
                logger.warn(
                    "SingleSelectionLazyListState: ${value.size} selectedKeys provided. " +
                        "Keeping only the first key."
                )
            }
            delegate.selectedKeys = if (value.isEmpty()) emptySet() else setOf(value.first())
        }

    /** Whether the user is currently navigating via keyboard. */
    public var isKeyboardNavigating: Boolean
        get() = delegate.isKeyboardNavigating
        set(value) {
            delegate.isKeyboardNavigating = value
        }

    /** The index of the last item that was made active, or null if no item has been activated yet. */
    public var lastActiveItemIndex: Int?
        get() = delegate.lastActiveItemIndex
        set(value) {
            delegate.lastActiveItemIndex = value
        }

    /**
     * Scrolls to the item at [itemIndex], optionally animating and applying a [scrollOffset].
     *
     * @param itemIndex The index of the item to scroll to.
     * @param animateScroll Whether to animate the scroll. Defaults to `false`.
     * @param scrollOffset Offset from the start of the item. Defaults to `0`.
     */
    public suspend fun scrollToItem(itemIndex: Int, animateScroll: Boolean = false, scrollOffset: Int = 0) {
        delegate.scrollToItem(itemIndex, animateScroll, scrollOffset)
    }

    /** Layout information about the currently visible items and overall list state. */
    public val layoutInfo: LazyListLayoutInfo
        get() = delegate.layoutInfo

    /** The index of the first item that is visible. */
    public val firstVisibleItemIndex: Int
        get() = delegate.firstVisibleItemIndex

    /** The scroll offset of the first visible item. */
    @Suppress("unused")
    public val firstVisibleItemScrollOffset: Int
        get() = delegate.firstVisibleItemScrollOffset

    /**
     * [InteractionSource] that will be used to dispatch drag events when this list is being dragged. If you want to
     * know whether the fling (or animated scroll) is in progress, use [isScrollInProgress].
     */
    public val interactionSource: InteractionSource
        get() = delegate.interactionSource
}

/**
 * Type-safe state holder for multi-selection lazy lists, enforcing [SelectionMode.Multiple] semantics. Delegates scroll
 * and layout to an underlying [SelectableLazyListState].
 */
public class MultiSelectionLazyListState internal constructor(internal val delegate: SelectableLazyListState) :
    ScrollableState by delegate, SelectableScope {
    /**
     * Type-safe state holder for multi-selection lazy lists.
     *
     * This state guarantees multi-selection semantics:
     * - [selectionMode] is always [SelectionMode.Multiple].
     * - [selectedKeys] accepts any number of keys.
     *
     * Use with [MultiSelectionLazyColumn].
     *
     * @param lazyListState Underlying [LazyListState] used for scroll/layout state.
     * @param initialSelectedKeys Optional initial selected keys applied once at creation.
     */
    public constructor(
        lazyListState: LazyListState,
        initialSelectedKeys: Set<Any> = emptySet(),
    ) : this(
        SelectableLazyListState(
            lazyListState = lazyListState,
            selectionMode = SelectionMode.Multiple,
            initialSelectedKeys = initialSelectedKeys,
        )
    )

    /** The underlying [LazyListState] used for scroll and layout. */
    public val lazyListState: LazyListState
        get() = delegate.lazyListState

    /** The selection mode, always [SelectionMode.Multiple] for this state. */
    public val selectionMode: SelectionMode
        get() = SelectionMode.Multiple

    /** The set of keys currently selected in the list. */
    override var selectedKeys: Set<Any>
        get() = delegate.selectedKeys
        set(value) {
            delegate.selectedKeys = value
        }

    /** Whether the user is currently navigating via keyboard. */
    public var isKeyboardNavigating: Boolean
        get() = delegate.isKeyboardNavigating
        set(value) {
            delegate.isKeyboardNavigating = value
        }

    /** The index of the last item that was made active, or null if no item has been activated yet. */
    public var lastActiveItemIndex: Int?
        get() = delegate.lastActiveItemIndex
        set(value) {
            delegate.lastActiveItemIndex = value
        }

    /**
     * Scrolls to the item at [itemIndex], optionally animating and applying a [scrollOffset].
     *
     * @param itemIndex The index of the item to scroll to.
     * @param animateScroll Whether to animate the scroll. Defaults to `false`.
     * @param scrollOffset Offset from the start of the item. Defaults to `0`.
     */
    public suspend fun scrollToItem(itemIndex: Int, animateScroll: Boolean = false, scrollOffset: Int = 0) {
        delegate.scrollToItem(itemIndex, animateScroll, scrollOffset)
    }

    /** Layout information about the currently visible items and overall list state. */
    public val layoutInfo: LazyListLayoutInfo
        get() = delegate.layoutInfo

    /** The index of the first item that is visible. */
    public val firstVisibleItemIndex: Int
        get() = delegate.firstVisibleItemIndex

    /** The scroll offset of the first visible item. */
    @Suppress("unused")
    public val firstVisibleItemScrollOffset: Int
        get() = delegate.firstVisibleItemScrollOffset

    /**
     * [InteractionSource] that will be used to dispatch drag events when this list is being dragged. If you want to
     * know whether the fling (or animated scroll) is in progress, use [isScrollInProgress].
     */
    public val interactionSource: InteractionSource
        get() = delegate.interactionSource
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

/** Extends [LazyItemScope] with [isSelected] and [isActive] flags for items inside a selectable lazy column. */
public interface SelectableLazyItemScope : LazyItemScope {
    /** Whether this item is currently selected. */
    public val isSelected: Boolean

    /**
     * Whether this item's parent list is currently focused. This reflects the list-wide focus state and is the same for
     * every item in the list.
     */
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
 * Legacy convenience overload for remembering a [SelectableLazyListState].
 *
 * This overload exists for source/binary compatibility and is equivalent to:
 * `rememberSelectableLazyListState(initialFirstVisibleItemIndex = firstVisibleItemIndex,`
 * `initialFirstVisibleItemScrollOffset = firstVisibleItemScrollOffset,` `selectionMode = SelectionMode.Multiple,
 * initialSelectedKeys = emptyList())`.
 *
 * Prefer the 4-parameter overload to control selection mode and initial selection explicitly.
 *
 * @param firstVisibleItemIndex Initial index of the first visible item, used only at creation time.
 * @param firstVisibleItemScrollOffset Initial scroll offset of the first visible item, used only at creation time.
 * @return The remembered state of the selectable lazy list.
 */
@Deprecated(
    message =
        "Use rememberSelectableLazyListState(" +
            "initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset, " +
            "selectionMode, initialSelectedKeys) instead.",
    replaceWith =
        ReplaceWith(
            "rememberSelectableLazyListState(" +
                "initialFirstVisibleItemIndex = firstVisibleItemIndex, " +
                "initialFirstVisibleItemScrollOffset = firstVisibleItemScrollOffset, " +
                "selectionMode = SelectionMode.Multiple, " +
                "initialSelectedKeys = emptyList())"
        ),
    level = DeprecationLevel.HIDDEN,
)
@Composable
public fun rememberSelectableLazyListState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0,
): SelectableLazyListState =
    rememberSelectableLazyListState(
        initialFirstVisibleItemIndex = firstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        selectionMode = SelectionMode.Multiple,
        initialSelectedKeys = emptyList(),
    )

/**
 * Remembers a legacy [SelectableLazyListState].
 *
 * The underlying [LazyListState] and [SelectableLazyListState] are created once and reused across recompositions.
 * Parameters are treated as initial values and are not reapplied after the first composition.
 *
 * [initialSelectedKeys] are normalized once at creation time using [normalizeFor]:
 * - [SelectionMode.None]: all keys are ignored.
 * - [SelectionMode.Single]: only the first key is kept.
 * - [SelectionMode.Multiple]: all keys are kept (deduplicated in insertion order).
 *
 * Initial key application does **not** trigger [SelectableLazyColumn]'s `onSelectedIndexesChange` on the first
 * composition.
 *
 * @param initialFirstVisibleItemIndex Initial index of the first visible item, used only at creation time.
 * @param initialFirstVisibleItemScrollOffset Initial scroll offset of the first visible item, used only at creation
 *   time.
 * @param selectionMode Legacy selection mode metadata and normalization mode used at creation time.
 * @param initialSelectedKeys Keys that should be selected before the first user interaction. This is a [List] (not a
 *   [Set]) so order is preserved for [SelectionMode.Single], where the first key wins. Used only at creation time;
 *   ignored on subsequent recompositions.
 */
@Composable
public fun rememberSelectableLazyListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    selectionMode: SelectionMode = SelectionMode.Multiple,
    initialSelectedKeys: List<Any> = emptyList(),
): SelectableLazyListState = remember {
    SelectableLazyListState(
        lazyListState = LazyListState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset),
        selectionMode = selectionMode,
        initialSelectedKeys = initialSelectedKeys.normalizeFor(selectionMode),
    )
}

/**
 * Remembers a [SingleSelectionLazyListState].
 *
 * The underlying [LazyListState] is created once and reused across recompositions. [initialSelectedKey] is applied only
 * at creation time and does not trigger [SelectableLazyColumn]'s `onSelectedIndexesChange` on first composition.
 *
 * @param initialFirstVisibleItemIndex Initial first visible item index, used only at creation time.
 * @param initialFirstVisibleItemScrollOffset Initial first visible item scroll offset, used only at creation time.
 * @param initialSelectedKey Optional preselected key for the initial composition.
 */
@Composable
public fun rememberSingleSelectionLazyListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    initialSelectedKey: Any? = null,
): SingleSelectionLazyListState = remember {
    SingleSelectionLazyListState(
        lazyListState = LazyListState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset),
        initialSelectedKey = initialSelectedKey,
    )
}

/**
 * Remembers a [MultiSelectionLazyListState].
 *
 * The underlying [LazyListState] is created once and reused across recompositions. [initialSelectedKeys] are applied
 * only at creation time and do not trigger [SelectableLazyColumn]'s `onSelectedIndexesChange` on first composition.
 *
 * @param initialFirstVisibleItemIndex Initial first visible item index, used only at creation time.
 * @param initialFirstVisibleItemScrollOffset Initial first visible item scroll offset, used only at creation time.
 * @param initialSelectedKeys Optional preselected keys for the initial composition.
 */
@Composable
public fun rememberMultiSelectionLazyListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    initialSelectedKeys: Set<Any> = emptySet(),
): MultiSelectionLazyListState = remember {
    MultiSelectionLazyListState(
        lazyListState = LazyListState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset),
        initialSelectedKeys = initialSelectedKeys,
    )
}

/**
 * Normalizes legacy initial selection input to the internal [Set]-based representation.
 *
 * The input is a [List] to preserve insertion order for deterministic legacy behavior.
 *
 * Behavior by [mode]:
 * - [SelectionMode.None]: returns an empty set and logs a warning if the list is not empty.
 * - [SelectionMode.Single]: returns a single-element set containing the first key; logs a warning when extra keys are
 *   provided.
 * - [SelectionMode.Multiple]: returns a [LinkedHashSet] preserving order and removing duplicates.
 */
private fun List<Any>.normalizeFor(mode: SelectionMode): Set<Any> =
    when (mode) {
        SelectionMode.None -> {
            if (isNotEmpty()) {
                logger.warn(
                    "SelectableLazyListState: $size initialSelectedKeys provided but " +
                        "SelectionMode.None disallows selection. All keys will be ignored: $this"
                )
            }
            emptySet()
        }

        SelectionMode.Single -> {
            if (size > 1) {
                val kept = first()
                val dropped = drop(1)
                logger.warn(
                    "SelectableLazyListState: $size initialSelectedKeys provided but " +
                        "SelectionMode.Single allows only one. Keeping '$kept', " +
                        "ignoring ${dropped.size} key(s): $dropped."
                )
            }
            if (isEmpty()) emptySet() else setOf(first())
        }

        SelectionMode.Multiple -> LinkedHashSet(this)
    }
