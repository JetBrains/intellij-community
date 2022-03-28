package org.jetbrains.jewel.theme.intellij.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skiko.Cursor
import kotlin.experimental.ExperimentalTypeInference
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun <T> Table(
    tableData: Array<Array<T>>,
    modifier: Modifier = Modifier,
    dividerWidth: Dp = 1.dp,
    content: @Composable BoxScope.(T) -> Unit
) {
    if (tableData.isEmpty()) return
    val columnsCount = tableData.first().size
    val rowSize = tableData.size
    val state = rememberLazyListState()
    // TODO validate model sizes

    val dividerWidthPx = with(LocalDensity.current) { dividerWidth.toPx().roundToInt() }
    BoxWithConstraints(modifier) {
        val tableWidthPx = with(LocalDensity.current) { maxWidth.toPx().roundToInt() }
        val cellWidthPx = remember(tableWidthPx, dividerWidthPx, columnsCount) {
            val totalDividersPx = dividerWidthPx * (columnsCount - 1)
            (tableWidthPx - totalDividersPx) / columnsCount
        }
        val dividerOffsets = remember {
            buildSnapshotMap {
                for (i in 0 until columnsCount - 1) {
                    put(i, (i + 1) * cellWidthPx + i * dividerWidthPx)
                }
            }
        }

        LazyColumn {
            items(rowSize) { rowIndex ->
                Layout(content = {
                    for (columnIndex in 0 until columnsCount) {
                        val cellId = TableLayoutId.Cell(columnIndex)
                        Box(modifier = Modifier.layoutId(cellId)) {
                            content(tableData[rowIndex][columnIndex])
                        }
                        if (columnIndex != columnsCount - 1) {
                            val dividerId = TableLayoutId.Divider(columnIndex)
                            Box(
                                modifier = Modifier.layoutId(dividerId)
                                    .width(dividerWidth)
                                    .background(Color.Green) // TODO pass this in/get from theme
                                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                                    .draggable(
                                        orientation = Orientation.Horizontal,
                                        state = rememberDraggableState { delta ->
                                            dividerOffsets[dividerId.dividerIndex] =
                                                dividerOffsets.getValue(dividerId.dividerIndex) + delta.roundToInt()
                                        }
                                    )
                            )
                        }
                    }
                }) { measurables, constraints ->
                    // First pass: calculate maxHeight
                    var maxHeight = 0
                    val measures = measurables.mapNotNull { measurable ->
                        val dimensions = when (val layoutId = measurable.layoutId) {
                            is TableLayoutId.Cell -> {
                                val newCellWidth = when (layoutId.cellIndex) {
                                    0 -> dividerOffsets.getValue(0)
                                    columnsCount - 1 -> tableWidthPx - dividerOffsets.getValue(layoutId.cellIndex - 1)
                                    else -> dividerOffsets.getValue(layoutId.cellIndex) - dividerOffsets.getValue(layoutId.cellIndex - 1) - dividerWidthPx
                                }
                                MeasurableDimensions(newCellWidth, measurable.maxIntrinsicHeight(cellWidthPx), measurable)
                            }
                            is TableLayoutId.Divider -> MeasurableDimensions(dividerWidthPx, measurable.maxIntrinsicHeight(cellWidthPx), measurable)
                            else -> null
                        }
                        dimensions?.let { maxHeight = max(maxHeight, it.height) }
                        dimensions
                    }

                    // Second pass: measure children with height = maxHeight
                    val placeables = measures.map { dimension ->
                        dimension.measurable.measure(Constraints.fixed(dimension.width, maxHeight))
                    }

                    // Set the size of the layout as big as it can
                    layout(constraints.maxWidth, maxHeight) {
                        // Track the y co-ord we have placed children up to
                        var xPosition = 0

                        // Place children in the parent layout
                        placeables.forEach { placeable ->
                            // Position item on the screen
                            placeable.placeRelative(x = xPosition, y = 0)

                            // Record the y co-ord placed up to
                            xPosition += placeable.width
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTypeInference::class)
private fun <K, V> buildSnapshotMap(
    @BuilderInference builder: SnapshotStateMap<K, V>.() -> Unit
) =
    SnapshotStateMap<K, V>().apply(builder)

private data class MeasurableDimensions(val width: Int, val height: Int, val measurable: Measurable) {

    override fun toString() = "[${Measurable::class.simpleName} $width x $height]"
}

private sealed class TableLayoutId {

    data class Divider(val dividerIndex: Int) : TableLayoutId()
    data class Cell(val cellIndex: Int) : TableLayoutId()
}

