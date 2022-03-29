@file:OptIn(ExperimentalTypeInference::class)

package org.jetbrains.jewel.theme.intellij.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

class TableModel<T>(
    val columnsCount: Int,
    private val contents: List<List<T>>
) {

    val rowsCount = contents.size

    init {
        require(contents.all { it.size == columnsCount }) { "One or more rows contain an unexpected number of cells" }
    }

    fun isEmpty() = contents.isEmpty()

    operator fun get(rowIndex: Int, columnIndex: Int): T {
        ensureValidCoordinates(rowIndex, columnIndex)
        return contents[rowIndex][columnIndex]
    }

    operator fun set(rowIndex: Int, columnIndex: Int, newContent: T): TableModel<T> {
        ensureValidCoordinates(rowIndex, columnIndex)
        val newRow = contents[rowIndex].replace(columnIndex, newContent)
        val newContents = contents.replace(rowIndex, newRow)
        return TableModel(columnsCount, newContents)
    }

    private fun <V> List<V>.replace(index: Int, newValue: V): List<V> = buildList {
        if (index > 0) addAll(this@replace.subList(0, index))
        add(newValue)
        if (index < this@replace.lastIndex) addAll(this@replace.subList(index + 1, rowsCount))
    }

    private fun ensureValidCoordinates(rowIndex: Int, columnIndex: Int) {
        require(rowIndex in 0 until rowsCount) { "Row index must be [0,$rowsCount)" }
        require(columnIndex in 0 until columnsCount) { "Column index must be [0,$columnsCount)" }
    }
}

inline fun <reified T> TableModel(rowsCount: Int, columnsCount: Int, contentsProducer: (Int, Int) -> T): TableModel<T> {
    val model: List<List<T>> = List(rowsCount) { i -> List(columnsCount) { j -> contentsProducer(i, j) } }
    return TableModel(columnsCount, model)
}

@Composable
fun <T> Table(
    tableData: TableModel<T>,
    modifier: Modifier = Modifier,
    dividerWidth: Dp = 1.dp,
    content: @Composable BoxScope.(T, Int, Int) -> Unit
) {
    if (tableData.isEmpty()) return

    val dividerWidthPx = with(LocalDensity.current) { dividerWidth.toPx() }
    var previousTableWidthPx: Float by remember { mutableStateOf(-1f) }

    BoxWithConstraints(modifier) {
        val tableWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

        // TODO replace with a proper state object (and validate to avoid overlaps etc)
        val dividerOffsets = remember {
            val totalDividersPx = dividerWidthPx * (tableData.columnsCount - 1)
            val cellWidthPx = (tableWidthPx - totalDividersPx) / tableData.columnsCount
            buildSnapshotMap {
                for (i in 0 until tableData.columnsCount - 1) {
                    put(i, ((i + 1) * cellWidthPx + i * dividerWidthPx))
                }
            }
        }

        if (previousTableWidthPx > 0) {
            val cellWidthDifference = (tableWidthPx - previousTableWidthPx) / tableData.columnsCount
            for (i in 0 until tableData.columnsCount - 1) {
                dividerOffsets[i] = dividerOffsets.getValue(i) + cellWidthDifference * (i + 1)
            }
        }

        previousTableWidthPx = tableWidthPx

        LazyColumn {
            items(tableData.rowsCount) { rowIndex ->
                Row(tableData.columnsCount, tableData, rowIndex, dividerOffsets, tableWidthPx, dividerWidthPx, content)
            }
        }
    }
}

@Composable
private fun <T> Row(
    columnsCount: Int,
    tableData: TableModel<T>,
    rowIndex: Int,
    dividerOffsets: SnapshotStateMap<Int, Float>,
    tableWidthPx: Float,
    dividerWidthPx: Float,
    content: @Composable (BoxScope.(T, Int, Int) -> Unit)
) {
    Layout(content = {
        for (columnIndex in 0 until columnsCount) {
            val cellId = TableLayoutId.Cell(columnIndex, columnIndex == columnsCount - 1)

            Box(modifier = Modifier.layoutId(cellId)) {
                content(tableData[rowIndex, columnIndex], rowIndex, columnIndex)
            }

            if (columnIndex != columnsCount - 1) {
                val dividerId = TableLayoutId.Divider(columnIndex)
                Box(
                    modifier = Modifier.layoutId(dividerId)
                        .fillMaxSize()
                        .background(Color.Green) // TODO pass this in/get from theme
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                dividerOffsets[dividerId.dividerIndex] =
                                    dividerOffsets.getValue(dividerId.dividerIndex) + delta
                            }
                        )
                )
            }
        }
    }) { measurables, constraints ->
        var maxHeightPx = 0
        var totalWidthPx = 0f
        val measures = measurables.map { measurable ->

            val elementWidth = when (val layoutId = measurable.layoutId) {
                is TableLayoutId.Cell -> when (layoutId.cellIndex) {
                    0 -> dividerOffsets.getValue(0)
                    columnsCount - 1 -> tableWidthPx - totalWidthPx
                    else -> dividerOffsets.getValue(layoutId.cellIndex) - dividerOffsets.getValue(layoutId.cellIndex - 1) - dividerWidthPx
                }
                is TableLayoutId.Divider -> dividerWidthPx
                else -> error("Unknown layoutId $layoutId")
            }.roundToInt()

            totalWidthPx += elementWidth

            val measuredHeightPx = measurable.maxIntrinsicHeight(elementWidth)
            maxHeightPx = max(maxHeightPx, measuredHeightPx)
            measurable.withWidth(elementWidth)
        }

        check(totalWidthPx == tableWidthPx) { "Table contents aren't taking up the whole width of the table" }

        val placeables = measures.map { dimension ->
            dimension.measurable.measure(Constraints.fixed(dimension.width, maxHeightPx))
        }

        layout(constraints.maxWidth, maxHeightPx) {
            var xPosition = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x = xPosition, y = 0)
                xPosition += placeable.width
            }
        }
    }
}

@OptIn(ExperimentalTypeInference::class)
private fun <K, V> buildSnapshotMap(
    @BuilderInference builder: SnapshotStateMap<K, V>.() -> Unit
) = SnapshotStateMap<K, V>().apply(builder)

private data class MeasurableWithWidth(val measurable: Measurable, val width: Int)

private fun Measurable.withWidth(width: Int) = MeasurableWithWidth(this, width)

private sealed class TableLayoutId {

    data class Divider(val dividerIndex: Int) : TableLayoutId()
    data class Cell(val cellIndex: Int, val isLast: Boolean) : TableLayoutId()
}

