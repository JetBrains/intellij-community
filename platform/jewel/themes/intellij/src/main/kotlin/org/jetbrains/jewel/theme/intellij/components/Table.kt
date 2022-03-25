package org.jetbrains.jewel.theme.intellij.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skiko.Cursor
import kotlin.math.roundToInt

@Composable
fun <T : Any> Table(
    tableData: Array<Array<T>>,
    modifier: Modifier = Modifier,
    dividerWidth: Dp = 1.dp,
    content: @Composable (T) -> Unit
) {
    var prevTableWidthPx by remember { mutableStateOf(-1) }
    BoxWithConstraints(modifier) {
        var elementXPositionsPx by remember { mutableStateOf(emptyList<Int>()) }

        val rowCount = tableData.size
        val columnCount = tableData.firstOrNull()?.size ?: 0

        val dividerSizePx = with(LocalDensity.current) { dividerWidth.toPx().roundToInt() }
        val tableWidthPx = with(LocalDensity.current) { maxWidth.toPx().roundToInt() }

        if (tableWidthPx < 0) return@BoxWithConstraints

        elementXPositionsPx = if (elementXPositionsPx.isEmpty()) {
            // TODO allow column weights
            calculateInitialOffsets(columnCount, dividerSizePx, tableWidthPx)
        } else {
            // Scale cells to accommodate table width changes
            val elementDeltaPx = (tableWidthPx - prevTableWidthPx) / columnCount
            // Skip first cell, that needs to stick at x = 0
            val elementsSize = elementXPositionsPx.size
            buildList(capacity = elementsSize) {
                for (i in 1 until elementsSize) {
                    val cumulativeOffsetPx = elementDeltaPx * i
                    add(elementXPositionsPx[i] + cumulativeOffsetPx)
                }
            }
        }

        prevTableWidthPx = tableWidthPx

        LazyColumn {
            if (elementXPositionsPx.isNotEmpty()) {
                items(rowCount) { rowIndex ->
                    require(tableData[rowIndex].size == columnCount) {
                        "Row $rowIndex has ${tableData[rowIndex].size} columns but was supposed to have $columnCount"
                    }

                    TableRow(
                        rowData = tableData[rowIndex],
                        rowWidthPx = tableWidthPx,
                        dividerWidth = dividerWidth,
                        elementXPositionsPx = elementXPositionsPx,
                        onElementPositionsChange = { elementXPositionsPx = it },
                        content = content
                    )
                }
            }
        }
    }
}

private data class MeasurableDimensions(val width: Int, val height: Int)

@Composable
private fun <T : Any> LazyItemScope.TableRow(
    rowData: Array<T>,
    rowWidthPx: Int,
    dividerWidth: Dp,
    elementXPositionsPx: List<Int>,
    onElementPositionsChange: (List<Int>) -> Unit,
    content: @Composable (T) -> Unit
) {
    val columnCount = rowData.size
    val columnAndDividersCount = columnCount * 2 - 1

    Layout(
        content = {
            repeat(columnAndDividersCount) { elementIndex ->
                if (elementIndex % 2 == 0) {
                    content(rowData[elementIndex / 2])
                } else {
                    Box(
                        modifier = Modifier
                            .width(dividerWidth)
                            .background(Color.Green) // TODO pass this in/get from theme
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    val newElementXPositionsPx = buildList(capacity = elementXPositionsPx.size) {
                                        addAll(elementXPositionsPx.subList(0, elementIndex))
                                        add(elementXPositionsPx[elementIndex] + delta.roundToInt())
                                        add(elementXPositionsPx[elementIndex + 1] + delta.roundToInt())

                                        if (elementIndex < elementXPositionsPx.size - 1) {
                                            addAll(elementXPositionsPx.subList(elementIndex + 1, elementXPositionsPx.size))
                                        }
                                    }
                                    onElementPositionsChange(newElementXPositionsPx)
                                }
                            )
                    )
                }
            }
        }
    ) { measurables, constraints ->
        // First pass: calculate maxHeight
        val measures = measurables.mapIndexed { elementIndex, measurable ->
            val nextElementX = if (elementIndex < elementXPositionsPx.lastIndex) elementXPositionsPx[elementIndex + 1] else rowWidthPx
            val elementWidth = nextElementX - elementXPositionsPx[elementIndex] - 1
            MeasurableDimensions(elementWidth, measurable.maxIntrinsicHeight(elementWidth))
        }

        val maxHeight = measures.maxByOrNull { it.height }!!.height

        // Second pass: measure children with height = maxHeight
        val placeables = measurables.mapIndexed { elementIndex, measurable ->
            measurable.measure(Constraints.fixed(measures[elementIndex].width, maxHeight))
        }

        // Set the size of the layout as big as it can
        layout(constraints.maxWidth, placeables.maxByOrNull { it.height }!!.height) {
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

private fun calculateInitialOffsets(columnCount: Int, dividerSizePx: Int, totalWidthPx: Int) = buildList(capacity = columnCount * 2 - 1) {
    val totalDividersWidthPx = (columnCount - 1) * dividerSizePx
    val totalAvailableWidthPx = totalWidthPx - totalDividersWidthPx
    val cellWidthPx = totalAvailableWidthPx / columnCount

    var currentXPx = 0
    for (i in 0 until columnCount * 2 - 1) {
        // Odd elements are dividers, even elements are cells
        add(currentXPx)
        currentXPx += if (i % 2 == 0) cellWidthPx else dividerSizePx
    }
}
