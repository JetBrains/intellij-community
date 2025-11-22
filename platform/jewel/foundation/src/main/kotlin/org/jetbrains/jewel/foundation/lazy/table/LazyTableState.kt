// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.layout.LazyLayoutPinnedItemList
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Remeasurement
import androidx.compose.ui.layout.RemeasurementModifier
import androidx.compose.ui.unit.Density
import kotlin.math.abs

/**
 * Creates and remembers a [LazyTableState] that can be used to control and observe a [LazyTable]'s scroll state.
 *
 * The state is saved and restored across configuration changes via [rememberSaveable]. You can customize the initial
 * visible position by providing the index of the first visible row/column and their scroll offsets.
 *
 * @param firstVisibleRowIndex the initial row index that should appear at the top of the viewport.
 * @param firstVisibleColumnIndex the initial column index that should appear at the start of the viewport.
 * @param firstVisibleRowScrollOffset the initial vertical scroll offset (in pixels) within the first visible row.
 * @param firstVisibleColumnScrollOffset the initial horizontal scroll offset (in pixels) within the first visible
 *   column.
 * @return a remembered [LazyTableState] instance.
 */
@Composable
public fun rememberLazyTableState(
    firstVisibleRowIndex: Int = 0,
    firstVisibleColumnIndex: Int = 0,
    firstVisibleRowScrollOffset: Int = 0,
    firstVisibleColumnScrollOffset: Int = 0,
): LazyTableState =
    rememberSaveable(saver = LazyTableState.Saver) {
        LazyTableState(
            firstVisibleRowIndex = firstVisibleRowIndex,
            firstVisibleColumnIndex = firstVisibleColumnIndex,
            firstVisibleRowScrollOffset = firstVisibleRowScrollOffset,
            firstVisibleColumnScrollOffset = firstVisibleColumnScrollOffset,
        )
    }

/**
 * State object that controls scrolling and exposes layout information for a [LazyTable].
 *
 * This state keeps track of the first visible row/column and their scroll offsets, provides APIs to scroll to specific
 * rows/columns (instantly or with animation), and exposes current [layoutInfo] and [tableInfo].
 *
 * Instances are typically obtained via [rememberLazyTableState].
 *
 * @param firstVisibleRowIndex the initial row index that should appear at the top of the viewport.
 * @param firstVisibleColumnIndex the initial column index that should appear at the start of the viewport.
 * @param firstVisibleRowScrollOffset the initial vertical scroll offset (in pixels) within the first visible row.
 * @param firstVisibleColumnScrollOffset the initial horizontal scroll offset (in pixels) within the first visible
 *   column.
 */
public class LazyTableState(
    firstVisibleRowIndex: Int = 0,
    firstVisibleColumnIndex: Int = 0,
    firstVisibleRowScrollOffset: Int = 0,
    firstVisibleColumnScrollOffset: Int = 0,
) {
    private val scrollPosition =
        LazyTableScrollPosition(
            firstVisibleRowIndex,
            firstVisibleColumnIndex,
            firstVisibleRowScrollOffset,
            firstVisibleColumnScrollOffset,
        )

    /** The index of the first visible row in the viewport. */
    public val firstVisibleRowIndex: Int
        get() = scrollPosition.row

    /** The index of the first visible column in the viewport. */
    public val firstVisibleColumnIndex: Int
        get() = scrollPosition.column

    /**
     * The vertical scroll offset, in pixels, into the [firstVisibleRowIndex].
     *
     * This is the number of pixels scrolled past the start of the first visible row.
     */
    public val firstVisibleItemVerticalScrollOffset: Int
        get() = scrollPosition.verticalScrollOffset

    /**
     * The horizontal scroll offset, in pixels, into the [firstVisibleColumnIndex].
     *
     * This is the number of pixels scrolled past the start of the first visible column.
     */
    public val firstVisibleItemHorizontalScrollOffset: Int
        get() = scrollPosition.horizontalScrollOffset

    internal val pinnedItems = LazyLayoutPinnedItemList()

    internal val awaitLayoutModifier = AwaitFirstLayoutModifier()

    internal val nearestRowRange: IntRange by scrollPosition.nearestRowRange

    internal val nearestColumnRange: IntRange by scrollPosition.nearestColumnRange

    internal var prefetchingEnabled: Boolean = true

    internal val prefetchState = LazyLayoutPrefetchState()

    internal val internalInteractionSource: MutableInteractionSource = MutableInteractionSource()

    internal var remeasurement: Remeasurement? = null
        private set

    private val layoutInfoState = mutableStateOf<LazyTableLayoutInfo>(EmptyLazyTableLayoutInfo)

    private val tableInfoState = mutableStateOf(LazyTableInfo.Empty)

    internal var numMeasurePasses: Int = 0
        private set

    internal val remeasurementModifier =
        object : RemeasurementModifier {
            override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
                this@LazyTableState.remeasurement = remeasurement
            }
        }

    internal fun applyTableInfo(tableInfo: LazyTableInfo) {
        tableInfoState.value = tableInfo
    }

    internal fun applyMeasureResult(result: LazyTableMeasureResult) {
        scrollPosition.updateFromMeasureResult(result)
        scrollToBeConsumedHorizontal -= result.consumedHorizontalScroll
        scrollToBeConsumedVertical -= result.consumedVerticalScroll

        layoutInfoState.value = result

        canHorizontalScrollBackward = result.canHorizontalScrollBackward
        canHorizontalScrollForward = result.canHorizontalScrollForward
        canVerticalScrollBackward = result.canVerticalScrollBackward
        canVerticalScrollForward = result.canVerticalScrollForward

        numMeasurePasses++
    }

    /** Whether a scroll (horizontal or vertical) is currently in progress. */
    public val isScrollInProgress: Boolean
        get() = horizontalScrollableState.isScrollInProgress || verticalScrollableState.isScrollInProgress

    /*
     * Horizontal scroll
     */

    internal var scrollToBeConsumedHorizontal = 0f
        private set

    /**
     * The underlying [ScrollableState] for horizontal scrolling.
     *
     * Most apps should prefer higher-level methods like [scrollToColumn] or [animateScrollToColumn]; this property is
     * primarily useful for advanced integrations.
     */
    public val horizontalScrollableState: ScrollableState = ScrollableState { -onHorizontalScroll(-it) }

    /**
     * Runs [block] to perform horizontal scrolling with the given [scrollPriority].
     *
     * This is a low-level API. Prefer [scrollToColumn] or [animateScrollToColumn] for most use cases.
     */
    public suspend fun horizontalScroll(
        scrollPriority: MutatePriority = MutatePriority.Default,
        block: suspend ScrollScope.() -> Unit,
    ) {
        awaitLayoutModifier.waitForFirstLayout()
        horizontalScrollableState.scroll(scrollPriority, block)
    }

    internal fun onHorizontalScroll(distance: Float): Float {
        val isValidScrollForward = distance < 0 && !canHorizontalScrollForward
        val isValidScrollBackward = distance > 0 && !canHorizontalScrollBackward
        if (isValidScrollForward || isValidScrollBackward) {
            return 0f
        }
        check(abs(scrollToBeConsumedHorizontal) <= 0.5f) {
            "entered drag with non-zero pending scroll: $scrollToBeConsumedHorizontal"
        }
        scrollToBeConsumedHorizontal += distance

        // scrollToBeConsumed will be consumed synchronously during the forceRemeasure invocation
        // inside measuring we do scrollToBeConsumed.roundToInt() so there will be no scroll if
        // we have less than 0.5 pixels
        if (abs(scrollToBeConsumedHorizontal) > 0.5f) {
            // val preScrollToBeConsumed = scrollToBeConsumedHorizontal
            remeasurement?.forceRemeasure()
            // if (prefetchingEnabled) {
            //      notifyPrefetch(preScrollToBeConsumed - scrollToBeConsumedVertical)
            // }
        }

        // here scrollToBeConsumed is already consumed during the forceRemeasure invocation
        if (abs(scrollToBeConsumedHorizontal) <= 0.5f) {
            // We consumed all of it - we'll hold onto the fractional scroll for later, so report
            // that we consumed the whole thing
            return distance
        } else {
            val scrollConsumed = distance - scrollToBeConsumedHorizontal
            // We did not consume all of it - return the rest to be consumed elsewhere (e.g.,
            // nested scrolling)
            scrollToBeConsumedHorizontal = 0f // We're not consuming the rest, give it back
            return scrollConsumed
        }
    }

    /** Whether additional content is available when scrolling forward (to the right). */
    public var canHorizontalScrollForward: Boolean by mutableStateOf(false)
        private set

    /** Whether additional content is available when scrolling backward (to the left). */
    public var canHorizontalScrollBackward: Boolean by mutableStateOf(false)
        private set

    /*
     * Vertical scroll
     */

    internal var scrollToBeConsumedVertical = 0f
        private set

    /**
     * The underlying [ScrollableState] for vertical scrolling.
     *
     * Most apps should prefer higher-level methods like [scrollToRow] or [animateScrollToRow]; this property is
     * primarily useful for advanced integrations.
     */
    public val verticalScrollableState: ScrollableState = ScrollableState { -onVerticalScroll(-it) }

    /**
     * Runs [block] to perform vertical scrolling with the given [scrollPriority].
     *
     * This is a low-level API. Prefer [scrollToRow] or [animateScrollToRow] for most use cases.
     */
    public suspend fun verticalScroll(
        scrollPriority: MutatePriority = MutatePriority.Default,
        block: suspend ScrollScope.() -> Unit,
    ) {
        awaitLayoutModifier.waitForFirstLayout()
        verticalScrollableState.scroll(scrollPriority, block)
    }

    internal fun onVerticalScroll(distance: Float): Float {
        val isValidScrollForward = distance < 0 && !canVerticalScrollForward
        val isValidScrollBackward = distance > 0 && !canVerticalScrollBackward
        if (isValidScrollForward || isValidScrollBackward) {
            return 0f
        }
        check(abs(scrollToBeConsumedVertical) <= 0.5f) {
            "entered drag with non-zero pending scroll: $scrollToBeConsumedVertical"
        }
        scrollToBeConsumedVertical += distance

        // scrollToBeConsumed will be consumed synchronously during the forceRemeasure invocation
        // inside measuring we do scrollToBeConsumed.roundToInt() so there will be no scroll if
        // we have less than 0.5 pixels
        if (abs(scrollToBeConsumedVertical) > 0.5f) {
            // val preScrollToBeConsumed = scrollToBeConsumedVertical
            remeasurement?.forceRemeasure()
            // if (prefetchingEnabled) {
            //     notifyPrefetch(preScrollToBeConsumed - scrollToBeConsumedVertical)
            // }
        }

        // here scrollToBeConsumed is already consumed during the forceRemeasure invocation
        if (abs(scrollToBeConsumedVertical) <= 0.5f) {
            // We consumed all of it - we'll hold onto the fractional scroll for later, so report
            // that we consumed the whole thing
            return distance
        } else {
            val scrollConsumed = distance - scrollToBeConsumedVertical
            // We did not consume all of it - return the rest to be consumed elsewhere (e.g.,
            // nested scrolling)
            scrollToBeConsumedVertical = 0f // We're not consuming the rest, give it back
            return scrollConsumed
        }
    }

    /** Whether additional content is available when scrolling forward (down). */
    public var canVerticalScrollForward: Boolean by mutableStateOf(false)
        private set

    /** Whether additional content is available when scrolling backward (up). */
    public var canVerticalScrollBackward: Boolean by mutableStateOf(false)
        private set

    /** The latest measured layout information for the table. */
    public val layoutInfo: LazyTableLayoutInfo
        get() = layoutInfoState.value

    /** The current table configuration information, as applied by the layout. */
    public val tableInfo: LazyTableInfo
        get() = tableInfoState.value

    /** Instantly scrolls the content so that [column] becomes the first visible column, with [scrollOffset] pixels. */
    public suspend fun scrollToColumn(column: Int, scrollOffset: Int = 0) {
        horizontalScroll { snapToColumnInternal(column, scrollOffset) }
    }

    internal fun snapToColumnInternal(column: Int, scrollOffset: Int) {
        scrollPosition.requestColumn(column, scrollOffset)
        // placement animation is not needed because we snap into a new position.
        // placementAnimator.reset()
        remeasurement?.forceRemeasure()
    }

    /** Instantly scrolls the content so that [row] becomes the first visible row, with [scrollOffset] pixels. */
    public suspend fun scrollToRow(row: Int, scrollOffset: Int = 0) {
        verticalScroll { snapToRowInternal(row, scrollOffset) }
    }

    internal fun snapToRowInternal(row: Int, scrollOffset: Int) {
        scrollPosition.requestRow(row, scrollOffset)
        // placement animation is not needed because we snap into a new position.
        // placementAnimator.reset()
        remeasurement?.forceRemeasure()
    }

    internal var density: Density = Density(1f, 1f)

    private val animateHorizontalScrollScope = LazyTableAnimateHorizontalScrollScope(this)
    private val animateVerticalScrollScope = LazyTableAnimateVerticalScrollScope(this)

    /** Smoothly scrolls so that [row] becomes visible at [scrollOffset] pixels from the top. */
    public suspend fun animateScrollToRow(row: Int, scrollOffset: Int = 0) {
        animateVerticalScrollScope.animateScrollToItem(row, scrollOffset)
    }

    /** Smoothly scrolls so that [column] becomes visible at [scrollOffset] pixels from the start. */
    public suspend fun animateScrollToColumn(column: Int, scrollOffset: Int = 0) {
        animateHorizontalScrollScope.animateScrollToItem(column, scrollOffset)
    }

    public companion object {
        /** A [Saver] to save and restore a [LazyTableState] across configuration changes. */
        public val Saver: Saver<LazyTableState, *> =
            listSaver(
                save = {
                    listOf(
                        it.firstVisibleRowIndex,
                        it.firstVisibleColumnIndex,
                        it.firstVisibleItemVerticalScrollOffset,
                        it.firstVisibleItemHorizontalScrollOffset,
                    )
                },
                restore = {
                    LazyTableState(
                        firstVisibleRowIndex = it[0],
                        firstVisibleColumnIndex = it[1],
                        firstVisibleRowScrollOffset = it[2],
                        firstVisibleColumnScrollOffset = it[3],
                    )
                },
            )
    }
}

/** Scope for low-level scroll operations used by animated scroll implementations. */
public interface TableScrollScope {
    /** Scrolls by the given [pixels] on each axis and returns the amount actually consumed. */
    public fun scrollBy(pixels: Size): Size
}
