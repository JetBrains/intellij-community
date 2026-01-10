// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.annotation.IntRange as AndroidXIntRange
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.layout.LazyLayoutPinnedItemList
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.annotation.FrequentlyChangingValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Remeasurement
import androidx.compose.ui.layout.RemeasurementModifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.fastRoundToInt
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.lazy.table.LazyTableState.Companion.Saver

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
 * @param prefetchStrategy the [LazyTablePrefetchStrategy] to use for prefetching content in this table.
 * @return a remembered [LazyTableState] instance.
 */
@Composable
public fun rememberLazyTableState(
    firstVisibleRowIndex: Int = 0,
    firstVisibleColumnIndex: Int = 0,
    firstVisibleRowScrollOffset: Int = 0,
    firstVisibleColumnScrollOffset: Int = 0,
    prefetchStrategy: LazyTablePrefetchStrategy = LazyTablePrefetchStrategy(),
): LazyTableState =
    rememberSaveable(saver = LazyTableState.Saver) {
        LazyTableState(
            firstVisibleRowIndex = firstVisibleRowIndex,
            firstVisibleColumnIndex = firstVisibleColumnIndex,
            firstVisibleRowScrollOffset = firstVisibleRowScrollOffset,
            firstVisibleColumnScrollOffset = firstVisibleColumnScrollOffset,
            prefetchStrategy = prefetchStrategy,
        )
    }

/**
 * Creates and remembers a [LazyTableState] with a cache window strategy for improved scroll performance.
 *
 * The cache window keeps items in memory beyond the visible viewport, reducing recomposition during scrolling. This
 * variant automatically creates a [LazyTableCacheWindowPrefetchStrategy] from the provided [cacheWindow].
 *
 * @param cacheWindow the [androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow] to use for caching items.
 * @param firstVisibleRowIndex the initial row index that should appear at the top of the viewport.
 * @param firstVisibleColumnIndex the initial column index that should appear at the start of the viewport.
 * @param firstVisibleRowScrollOffset the initial vertical scroll offset (in pixels) within the first visible row.
 * @param firstVisibleColumnScrollOffset the initial horizontal scroll offset (in pixels) within the first visible
 *   column.
 * @return a remembered [LazyTableState] instance.
 */
@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
public fun rememberLazyTableState(
    cacheWindow: androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow,
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
            prefetchStrategy = LazyTableCacheWindowPrefetchStrategy(cacheWindow),
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
 * @param prefetchStrategy the [LazyTablePrefetchStrategy] to use for prefetching content in this table.
 */
public class LazyTableState(
    firstVisibleRowIndex: Int = 0,
    firstVisibleColumnIndex: Int = 0,
    firstVisibleRowScrollOffset: Int = 0,
    firstVisibleColumnScrollOffset: Int = 0,
    internal val prefetchStrategy: LazyTablePrefetchStrategy = LazyTablePrefetchStrategy(),
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

    internal val prefetchState = LazyLayoutPrefetchState {
        with(prefetchStrategy) {
            onNestedPrefetch(
                firstVisibleCellCoordinate =
                    Snapshot.withoutReadObservation { IntOffset(firstVisibleColumnIndex, firstVisibleRowIndex) },
                layoutInfo = Snapshot.withoutReadObservation { layoutInfoState.value },
            )
        }
    }

    internal val internalInteractionSource: MutableInteractionSource = MutableInteractionSource()

    internal var remeasurement: Remeasurement? = null
        private set

    private val layoutInfoState = mutableStateOf(EmptyLazyTableLayoutInfo, neverEqualPolicy())

    internal var approachLayoutInfo: LazyTableMeasureResult? = null
        private set

    private val tableInfoState = mutableStateOf(LazyTableInfo.Empty)

    internal val placementScopeInvalidator = ObservableScopeInvalidator()

    internal val measurementScopeInvalidator = ObservableScopeInvalidator()

    internal var hasLookaheadOccurred: Boolean = false
        private set

    @Suppress("ktlint:standard:backing-property-naming")
    private val _lazyTableScrollDeltaBetweenPasses = LazyTableScrollDeltaBetweenPasses()

    internal val scrollDeltaBetweenPasses: Offset
        get() = _lazyTableScrollDeltaBetweenPasses.scrollDeltaBetweenPasses

    private var lastRemeasureTime = 0L
    private val remeasureThrottleMs = 16 // ~60fps

    internal val remeasurementModifier =
        object : RemeasurementModifier {
            override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
                this@LazyTableState.remeasurement = remeasurement
            }
        }

    internal fun applyTableInfo(tableInfo: LazyTableInfo) {
        tableInfoState.value = tableInfo
    }

    internal fun applyMeasureResult(
        result: LazyTableMeasureResult,
        isLookingAhead: Boolean,
        visibleItemsStayedTheSame: Boolean = false,
    ) {
        if (!isLookingAhead && hasLookaheadOccurred) {
            // If there was already a lookahead pass, record this result as approach result
            approachLayoutInfo = result
            Snapshot.withoutReadObservation {
                @Suppress("ComplexCondition")
                if (
                    _lazyTableScrollDeltaBetweenPasses.isActive &&
                        result.firstFloatingCell?.row == scrollPosition.row &&
                        result.firstFloatingCell.column == scrollPosition.column &&
                        result.firstFloatingCellScrollOffset.x == scrollPosition.horizontalScrollOffset &&
                        result.firstFloatingCellScrollOffset.y == scrollPosition.verticalScrollOffset
                ) {
                    _lazyTableScrollDeltaBetweenPasses.stop()
                }
            }
        } else {
            if (isLookingAhead) {
                hasLookaheadOccurred = true
            }

            scrollToBeConsumedHorizontal -= result.consumedHorizontalScroll
            scrollToBeConsumedVertical -= result.consumedVerticalScroll

            layoutInfoState.value = result

            canHorizontalScrollBackward = result.canHorizontalScrollBackward
            canHorizontalScrollForward = result.canHorizontalScrollForward
            canVerticalScrollBackward = result.canVerticalScrollBackward
            canVerticalScrollForward = result.canVerticalScrollForward

            // Notify prefetch strategy when visible items change
            if (visibleItemsStayedTheSame) {
                scrollPosition.updateScrollOffset(result.firstFloatingCellScrollOffset)
            } else {
                scrollPosition.updateFromMeasureResult(result)
                notifyPrefetchOnVisibleItemsUpdated(result)
            }

            if (isLookingAhead) {
                _lazyTableScrollDeltaBetweenPasses.updateScrollDeltaForApproach(
                    result.scrollBackAmount,
                    result.density,
                    result.coroutineScope,
                )
            }
        }
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
            val intDelta = scrollToBeConsumedHorizontal.fastRoundToInt()
            var scrolledLayoutInfo =
                layoutInfoState.value.copyWithScrollDeltaWithoutRemeasure(
                    delta = IntOffset(intDelta, 0),
                    updateAnimations = !hasLookaheadOccurred,
                )

            if (scrolledLayoutInfo != null && this.approachLayoutInfo != null) {
                val scrolledApproachLayoutInfo =
                    approachLayoutInfo?.copyWithScrollDeltaWithoutRemeasure(
                        delta = IntOffset(intDelta, 0),
                        updateAnimations = true,
                    )

                if (scrolledApproachLayoutInfo != null) {
                    approachLayoutInfo = scrolledApproachLayoutInfo
                } else {
                    scrolledLayoutInfo = null
                }
            }

            if (scrolledLayoutInfo != null) {
                applyMeasureResult(
                    result = scrolledLayoutInfo,
                    isLookingAhead = hasLookaheadOccurred,
                    visibleItemsStayedTheSame = true,
                )
                placementScopeInvalidator.invalidateScope()
                notifyPrefetchOnScroll(Offset(distance, 0f), scrolledLayoutInfo)
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastRemeasureTime >= remeasureThrottleMs) {
                    remeasurement?.forceRemeasure()
                    notifyPrefetchOnScroll(Offset(distance, 0f), layoutInfoState.value)
                    lastRemeasureTime = currentTime
                }
            }
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
            val intDelta = scrollToBeConsumedVertical.fastRoundToInt()
            var scrolledLayoutInfo =
                layoutInfoState.value.copyWithScrollDeltaWithoutRemeasure(
                    delta = IntOffset(0, intDelta),
                    updateAnimations = !hasLookaheadOccurred,
                )

            if (scrolledLayoutInfo != null && this.approachLayoutInfo != null) {
                val scrolledApproachLayoutInfo =
                    approachLayoutInfo?.copyWithScrollDeltaWithoutRemeasure(
                        delta = IntOffset(0, intDelta),
                        updateAnimations = true,
                    )

                if (scrolledApproachLayoutInfo != null) {
                    approachLayoutInfo = scrolledApproachLayoutInfo
                } else {
                    scrolledLayoutInfo = null
                }
            }

            if (scrolledLayoutInfo != null) {
                applyMeasureResult(
                    result = scrolledLayoutInfo,
                    isLookingAhead = hasLookaheadOccurred,
                    visibleItemsStayedTheSame = true,
                )
                placementScopeInvalidator.invalidateScope()
                notifyPrefetchOnScroll(Offset(0f, distance), scrolledLayoutInfo)
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastRemeasureTime >= remeasureThrottleMs) {
                    remeasurement?.forceRemeasure()
                    notifyPrefetchOnScroll(Offset(0f, distance), layoutInfoState.value)
                    lastRemeasureTime = currentTime
                }
            }
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
        @FrequentlyChangingValue get() = layoutInfoState.value

    /** The current table configuration information, as applied by the layout. */
    public val tableInfo: LazyTableInfo
        get() = tableInfoState.value

    /** Instantly scrolls the content so that [column] becomes the first visible column, with [scrollOffset] pixels. */
    public suspend fun scrollToColumn(@AndroidXIntRange(from = 0) column: Int, scrollOffset: Int = 0) {
        horizontalScroll { snapToColumnInternal(column, scrollOffset, forceRemeasure = true) }
    }

    public fun requestScrollToItem(@AndroidXIntRange(from = 0) column: Int, scrollOffset: Int = 0) {
        if (isScrollInProgress) {
            layoutInfoState.value.coroutineScope.launch { horizontalScroll {} }
        }
        snapToColumnInternal(column, scrollOffset, forceRemeasure = false)
    }

    internal fun snapToColumnInternal(column: Int, scrollOffset: Int, forceRemeasure: Boolean) {
        val positionChanged = scrollPosition.column != column || scrollPosition.horizontalScrollOffset != scrollOffset

        if (positionChanged) {
            // itemAnimator.reset()
            (prefetchStrategy as? LazyTableCacheWindowPrefetchStrategy)?.resetStrategy()
        }

        scrollPosition.requestColumn(column, scrollOffset)

        if (forceRemeasure) {
            remeasurement?.forceRemeasure()
        } else {
            measurementScopeInvalidator.invalidateScope()
        }
    }

    /** Instantly scrolls the content so that [row] becomes the first visible row, with [scrollOffset] pixels. */
    public suspend fun scrollToRow(@AndroidXIntRange(from = 0) row: Int, scrollOffset: Int = 0) {
        verticalScroll { snapToRowInternal(row, scrollOffset, true) }
    }

    public fun requestScrollToRow(@AndroidXIntRange(from = 0) row: Int, scrollOffset: Int = 0) {
        if (isScrollInProgress) {
            layoutInfoState.value.coroutineScope.launch { verticalScroll {} }
        }
        snapToRowInternal(row, scrollOffset, forceRemeasure = true)
    }

    internal fun snapToRowInternal(row: Int, scrollOffset: Int, forceRemeasure: Boolean) {
        val positionChanged = scrollPosition.row != row || scrollPosition.verticalScrollOffset != scrollOffset

        if (positionChanged) {
            //            itemAnimator.reset()
            (prefetchStrategy as? LazyTableCacheWindowPrefetchStrategy)?.resetStrategy()
        }

        scrollPosition.requestRow(row, scrollOffset)

        if (forceRemeasure) {
            remeasurement?.forceRemeasure()
        } else {
            measurementScopeInvalidator.invalidateScope()
        }
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

    private val prefetchScope: LazyTablePrefetchScope =
        object : LazyTablePrefetchScope {
            override fun scheduleRowPrefetch(rowIndex: Int): List<LazyLayoutPrefetchState.PrefetchHandle> {
                val handles = mutableListOf<LazyLayoutPrefetchState.PrefetchHandle>()
                Snapshot.withoutReadObservation {
                    val measureResult = layoutInfoState.value
                    val itemsInRow = measureResult.rowPrefetchInfoRetriever(rowIndex)
                    itemsInRow.forEach { (index, constraints) ->
                        handles.add(prefetchState.schedulePrecompositionAndPremeasure(index, constraints))
                    }
                }
                return handles
            }

            override fun scheduleColumnPrefetch(columnIndex: Int): List<LazyLayoutPrefetchState.PrefetchHandle> {
                val handles = mutableListOf<LazyLayoutPrefetchState.PrefetchHandle>()
                Snapshot.withoutReadObservation {
                    val measureResult = layoutInfoState.value
                    val itemsInColumn = measureResult.columnPrefetchInfoRetriever(columnIndex)
                    itemsInColumn.forEach { (index, constraints) ->
                        handles.add(prefetchState.schedulePrecompositionAndPremeasure(index, constraints))
                    }
                }
                return handles
            }

            override fun scheduleCellPrefetch(column: Int, row: Int): LazyLayoutPrefetchState.PrefetchHandle {
                val layoutInfo = Snapshot.withoutReadObservation { layoutInfoState.value }
                // Calculate linear index for the cell
                val index = row * layoutInfo.columns + column
                return prefetchState.schedulePrecomposition(index)
            }
        }

    private fun notifyPrefetchOnScroll(delta: Offset, layoutInfo: LazyTableLayoutInfo) {
        if (prefetchingEnabled) {
            with(prefetchStrategy) { prefetchScope.onScroll(delta, layoutInfo) }
        }
    }

    private fun notifyPrefetchOnVisibleItemsUpdated(layoutInfo: LazyTableLayoutInfo) {
        if (prefetchingEnabled) {
            with(prefetchStrategy) { prefetchScope.onVisibleItemsUpdated(layoutInfo) }
        }
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
