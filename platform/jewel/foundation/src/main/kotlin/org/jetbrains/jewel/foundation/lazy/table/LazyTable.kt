// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("KDocUnresolvedReference")

package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasureScope
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import org.jetbrains.jewel.foundation.theme.LocalTextStyle

/**
 * A lazy table component that efficiently displays large datasets in a grid layout with support for pinned rows and
 * columns.
 *
 * Provides a high-performance scrollable table that only composes and lays out visible cells, similar to [LazyColumn]
 * and [LazyRow]. The table supports pinned (frozen) rows and columns that remain visible during scrolling, making it
 * ideal for spreadsheet-like interfaces and data grids with headers. The component handles both vertical and horizontal
 * scrolling with configurable fling behavior and overscroll effects.
 *
 * Features:
 * - Lazy composition: only visible cells are composed and measured
 * - Pinned rows and columns for sticky headers
 * - Bidirectional scrolling with independent vertical and horizontal control
 * - Content padding for insets and spacing
 * - Configurable cell spacing through horizontal and vertical arrangements
 * - Overscroll effects and custom fling behavior
 * - Beyond-bounds prefetching for smooth scrolling
 *
 * **Usage example:**
 * [`Tables.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Tables.kt)
 *
 * **Swing equivalent:**
 * [`JBTable`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/table/JBTable.java)
 * and [`JTable`](https://docs.oracle.com/javase/tutorial/uiswing/components/table.html)
 *
 * @param modifier Modifier to be applied to the table
 * @param state The state object controlling the table's scroll position and layout information
 * @param pinnedColumns The number of columns to pin (freeze) at the start of the table. These columns remain visible
 *   during horizontal scrolling
 * @param pinnedRows The number of rows to pin (freeze) at the top of the table. These rows remain visible during
 *   vertical scrolling
 * @param contentPadding Padding around the table content
 * @param flingBehavior Logic describing fling behavior when scrolling by gesture input
 * @param overscrollEffect Effect applied when scrolling beyond the table bounds. Pass null to disable overscroll
 * @param userScrollEnabled Controls whether the table can be scrolled by user gestures. When false, the table can only
 *   be scrolled programmatically
 * @param beyondBoundsItemCount The number of items to compose and measure beyond the visible bounds for smoother
 *   scrolling
 * @param horizontalArrangement Arrangement strategy for horizontal spacing between cells
 * @param verticalArrangement Arrangement strategy for vertical spacing between cells
 * @param content The DSL content block for defining table cells. Use this to specify the table structure and cell
 *   content
 * @see com.intellij.ui.table.JBTable
 * @see javax.swing.JTable
 * @see androidx.compose.foundation.lazy.LazyColumn
 * @see androidx.compose.foundation.lazy.LazyRow
 */
@Composable
public fun LazyTable(
    modifier: Modifier = Modifier,
    state: LazyTableState = rememberLazyTableState(),
    pinnedColumns: Int = 0,
    pinnedRows: Int = 0,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    userScrollEnabled: Boolean = true,
    beyondBoundsItemCount: Int = 0,
    horizontalArrangement: Arrangement.Horizontal? = null,
    verticalArrangement: Arrangement.Vertical? = null,
    content: LazyTableScope.() -> Unit,
) {
    val itemProviderLambda = rememberLazyTableItemProviderLambda(state, pinnedColumns, pinnedRows, content)

    val measurePolicy =
        rememberLazyTabletMeasurePolicy(
            itemProviderLambda = itemProviderLambda,
            state = state,
            pinnedColumns = pinnedColumns,
            pinnedRows = pinnedRows,
            contentPadding = contentPadding,
            beyondBoundsItemCount = beyondBoundsItemCount,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = verticalArrangement,
        )

    LazyLayout(
        modifier =
            modifier
                .then(state.remeasurementModifier)
                .then(state.awaitLayoutModifier)
                .overscroll(overscrollEffect)
                .scrollable(
                    orientation = Orientation.Vertical,
                    interactionSource = state.internalInteractionSource,
                    reverseDirection =
                        ScrollableDefaults.reverseDirection(LocalLayoutDirection.current, Orientation.Vertical, false),
                    flingBehavior = flingBehavior,
                    state = state.verticalScrollableState,
                    overscrollEffect = overscrollEffect,
                    enabled = userScrollEnabled,
                )
                .scrollable(
                    orientation = Orientation.Horizontal,
                    interactionSource = state.internalInteractionSource,
                    reverseDirection =
                        ScrollableDefaults.reverseDirection(
                            LocalLayoutDirection.current,
                            Orientation.Horizontal,
                            false,
                        ),
                    flingBehavior = flingBehavior,
                    state = state.horizontalScrollableState,
                    overscrollEffect = overscrollEffect,
                    enabled = userScrollEnabled,
                )
                .clip(RectangleShape),
        prefetchState = state.prefetchState,
        measurePolicy = measurePolicy,
        itemProvider = itemProviderLambda,
    )
}

@Composable
internal fun rememberLazyTabletMeasurePolicy(
    itemProviderLambda: () -> LazyTableItemProvider,
    state: LazyTableState,
    pinnedColumns: Int,
    pinnedRows: Int,
    contentPadding: PaddingValues,
    beyondBoundsItemCount: Int,
    horizontalArrangement: Arrangement.Horizontal? = null,
    verticalArrangement: Arrangement.Vertical? = null,
    textStyle: TextStyle = LocalTextStyle.current,
): LazyLayoutMeasureScope.(Constraints) -> MeasureResult =
    remember<LazyLayoutMeasureScope.(Constraints) -> MeasureResult>(
        itemProviderLambda,
        state,
        pinnedColumns,
        pinnedRows,
        contentPadding,
        beyondBoundsItemCount,
    ) {
        { containerConstraints ->
            check(
                containerConstraints.maxHeight != Constraints.Infinity &&
                    containerConstraints.maxWidth != Constraints.Infinity
            ) {
                "LazyTable does not support infinite constraints."
            }

            state.density = this

            val startPadding = contentPadding.calculateStartPadding(layoutDirection).roundToPx()
            val endPadding = contentPadding.calculateEndPadding(layoutDirection).roundToPx()
            val topPadding = contentPadding.calculateTopPadding().roundToPx()
            val bottomPadding = contentPadding.calculateBottomPadding().roundToPx()

            val totalVerticalPadding = topPadding + bottomPadding
            val totalHorizontalPadding = startPadding + endPadding

            val contentConstraints = containerConstraints.offset(-totalHorizontalPadding, -totalVerticalPadding)

            val availableSize =
                IntSize(
                    containerConstraints.maxWidth - totalHorizontalPadding,
                    containerConstraints.maxHeight - totalVerticalPadding,
                )

            val visualItemOffset = IntOffset(startPadding, topPadding)

            val horizontalSpacing = horizontalArrangement?.spacing?.roundToPx() ?: 0
            val verticalSpacing = verticalArrangement?.spacing?.roundToPx() ?: 0

            val itemProvider = itemProviderLambda()
            val measuredItemProvider =
                object :
                    LazyTableMeasuredItemProvider(
                        availableSize = availableSize,
                        rows = itemProvider.rowCount,
                        columns = itemProvider.columnCount,
                        horizontalSpacing = horizontalSpacing,
                        verticalSpacing = verticalSpacing,
                        itemProvider = itemProvider,
                        measureScope = this,
                        density = this,
                        textStyle = textStyle,
                    ) {
                    override fun createItem(
                        column: Int,
                        row: Int,
                        size: IntSize,
                        key: Any,
                        contentType: Any?,
                        placeables: List<Placeable>,
                    ): LazyTableMeasuredItem {
                        // we add spaceBetweenItems as an extra spacing for all items apart from the last one so
                        // the lazy list measuring logic will take it into account.
                        val coordinate = IntOffset(column, row)
                        val index = itemProvider.getIndex(coordinate)
                        return LazyTableMeasuredItem(
                            index = index,
                            row = row,
                            column = column,
                            size = size,
                            placeables = placeables,
                            alignment = Alignment.TopStart,
                            layoutDirection = layoutDirection,
                            visualOffset = visualItemOffset,
                            key = key,
                            contentType = contentType,
                        )
                    }
                }

            val firstVisibleItemCoordinate: IntOffset
            val firstVisibleScrollOffset: IntOffset

            Snapshot.withoutReadObservation {
                firstVisibleItemCoordinate = IntOffset(state.firstVisibleColumnIndex, state.firstVisibleRowIndex)
                firstVisibleScrollOffset =
                    IntOffset(state.firstVisibleItemHorizontalScrollOffset, state.firstVisibleItemVerticalScrollOffset)
            }

            state.applyTableInfo(
                LazyTableInfo(
                    columns = itemProvider.columnCount,
                    rows = itemProvider.rowCount,
                    pinnedColumns = pinnedColumns,
                    pinnedRows = pinnedRows,
                )
            )

            measureLazyTable(
                    constraints = contentConstraints,
                    availableSize = availableSize,
                    rows = itemProvider.rowCount,
                    columns = itemProvider.columnCount,
                    pinnedColumns = minOf(pinnedColumns, itemProvider.columnCount),
                    pinnedRows = minOf(pinnedRows, itemProvider.rowCount),
                    measuredItemProvider = measuredItemProvider,
                    horizontalSpacing = horizontalSpacing,
                    verticalSpacing = verticalSpacing,
                    firstVisibleCellPosition = firstVisibleItemCoordinate,
                    firstVisibleCellScrollOffset = firstVisibleScrollOffset,
                    scrollToBeConsumed = Offset(state.scrollToBeConsumedHorizontal, state.scrollToBeConsumedVertical),
                    beyondBoundsItemCount = beyondBoundsItemCount,
                    layout = { width, height, placement ->
                        layout(
                            containerConstraints.constrainWidth(width + totalHorizontalPadding),
                            containerConstraints.constrainHeight(height + totalVerticalPadding),
                            emptyMap(),
                            placement,
                        )
                    },
                )
                .also { state.applyMeasureResult(it) }
        }
    }
