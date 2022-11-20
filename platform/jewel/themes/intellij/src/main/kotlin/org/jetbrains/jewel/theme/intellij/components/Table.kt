@file:OptIn(ExperimentalTypeInference::class)

package org.jetbrains.jewel.theme.intellij.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlin.math.min
import kotlin.math.roundToInt

class Table<T> internal constructor(
    val columnsCount: Int,
    private val contents: List<List<T>>
) {

    val rowsCount
        get() = contents.size

    init {
        require(contents.all { it.size == columnsCount }) { "One or more rows contain an unexpected number of cells" }
    }

    fun isEmpty() = contents.isEmpty()

    operator fun get(rowIndex: Int, columnIndex: Int): T {
        ensureValidCoordinates(rowIndex, columnIndex)
        return contents[rowIndex][columnIndex]
    }

    operator fun set(rowIndex: Int, columnIndex: Int, newContent: T): Table<T> {
        ensureValidCoordinates(rowIndex, columnIndex)
        val newRow = contents[rowIndex].replace(columnIndex, newContent)
        val newContents = contents.replace(rowIndex, newRow)
        return Table(columnsCount, newContents)
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

fun <T> Table(rowsCount: Int, columnsCount: Int, contentsProducer: (Int, Int) -> T): Table<T> {
    val model: List<List<T>> = List(rowsCount) { i -> List(columnsCount) { j -> contentsProducer(i, j) } }
    return Table(columnsCount, model)
}

interface TableDividersState {

    val dividersCount: Int
    val lastDividerIndex: Int

    fun updateDividerOffset(dividerIndex: Int, delta: Float)

    fun getDividerOffset(dividerIndex: Int): Float
}

val TableDividersState.dividersIndices
    get() = 0 until dividersCount

val TableDividersState.offsets
    get() = dividersIndices.map { getDividerOffset(it) }

class TableState(
    initialFirstVisibleRowIndex: Int = 0,
    initialFirstVisibleRowScrollOffset: Int = 0,
    initialColumnsSizeInPx: Map<Int, Float> = emptyMap()
) : TableDividersState {

    companion object {

        val Saver: Saver<TableState, *> = mapSaver(
            save = {
                mapOf(
                    "initialFirstVisibleRowIndex" to it.firstVisibleRowIndex,
                    "initialFirstVisibleRowScrollOffset" to it.firstVisibleRowScrollOffset,
                    *it.dividerOffsets.map { (_, v) -> it.toString() to v }.toTypedArray()
                )
            },
            restore = {
                val mutableMap = it.toMutableMap()
                TableState(
                    initialFirstVisibleRowIndex = mutableMap.remove("initialFirstVisibleRowIndex") as Int,
                    initialFirstVisibleRowScrollOffset = mutableMap.remove("initialFirstVisibleRowScrollOffset") as Int,
                    initialColumnsSizeInPx = mutableMap.entries.associate { (k, v) -> k.toInt() to (v as Float) }
                )
            }
        )
    }

    internal val listState = LazyListState(initialFirstVisibleRowIndex, initialFirstVisibleRowScrollOffset)
    internal val dividerOffsets = SnapshotStateMap<Int, Float>().apply { putAll(initialColumnsSizeInPx) }

    val firstVisibleRowIndex
        get() = listState.firstVisibleItemIndex

    val firstVisibleRowScrollOffset
        get() = listState.firstVisibleItemScrollOffset

    private var dividerWidthPx = 1f
    private var tableWidthPx = 1f
    private var columnsCount = 0

    override val dividersCount
        get() = columnsCount - 1

    override val lastDividerIndex
        get() = dividersCount - 1

    private var isInitialized = false

    internal fun initializeState(tableWidthPx: Float, dividerWidthPx: Float, columnsCount: Int) {
        if (isInitialized) return

        isInitialized = true
        if (dividerOffsets.isNotEmpty()) {
            val sortedMap = dividerOffsets.entries.sortedBy { it.key }
            require(sortedMap.map { it.value }.isSorted()) { "One or more offset is not ordered" }
            require(sortedMap.last().value + dividerWidthPx <= tableWidthPx) { "Last element is outside the table." }
            require(dividerOffsets.size == columnsCount - 1) {
                "initialColumnsSizeInPx has size ${dividerOffsets.size} while current model has $columnsCount columns"
            }
        } else {
            val totalDividersPx = dividerWidthPx * (columnsCount - 1)
            val cellWidthPx = (tableWidthPx - totalDividersPx) / columnsCount

            for (i in 0 until columnsCount - 1) {
                dividerOffsets[i] = (i + 1) * cellWidthPx + i * dividerWidthPx
            }
        }

        this.tableWidthPx = tableWidthPx
        this.dividerWidthPx = dividerWidthPx
        this.columnsCount = columnsCount
    }

    internal fun onTableResize(newTableWidthPx: Float) {
        checkInitialized()
        if (newTableWidthPx == tableWidthPx) return
        val scaleFactor = newTableWidthPx / tableWidthPx
        for (i in 0 until columnsCount - 1) {
            dividerOffsets[i] = dividerOffsets.getValue(i) * scaleFactor
        }
        tableWidthPx = newTableWidthPx
    }

    override fun updateDividerOffset(dividerIndex: Int, delta: Float) {
        checkInitialized()
        check(dividerIndex in 0 until columnsCount) { "Invalid divider index $dividerIndex" }
        if (delta == 0f) return
        val currentOffset = dividerOffsets.getValue(dividerIndex)
        val newOffset = when {
            delta > 0 && dividerIndex == lastDividerIndex -> min(currentOffset + delta, tableWidthPx - dividerWidthPx - 1)
            delta > 0 -> min(currentOffset + delta, dividerOffsets.getValue(dividerIndex + 1) - dividerWidthPx - 1)
            delta < 0 && dividerIndex == 0 -> max(currentOffset + delta, 1f)
            delta < 0 -> max(currentOffset + delta, dividerOffsets.getValue(dividerIndex - 1) + dividerWidthPx + 1)
            else -> error("How did I get here?")
        }
        dividerOffsets[dividerIndex] = newOffset
    }

    override fun getDividerOffset(dividerIndex: Int): Float {
        checkInitialized()
        return dividerOffsets.getValue(dividerIndex)
    }

    private fun checkInitialized() {
        check(isInitialized) { "State has not been initialized." }
    }

    suspend fun animateScrollToRow(
        index: Int,
        scrollOffset: Int = 0
    ) = listState.animateScrollToItem(index, scrollOffset)

    suspend fun scrollToRow(
        index: Int,
        scrollOffset: Int = 0
    ) = listState.scrollToItem(index, scrollOffset)
}

private fun <T : Comparable<T>> Iterable<T>.isSorted(): Boolean {
    var previous: T? = null
    forEach { current ->
        when {
            previous == null -> previous = current
            previous!! > current -> return false
        }
    }
    return true
}

@Composable
fun rememberTableState(
    initialFirstVisibleRowIndex: Int = 0,
    initialFirstVisibleRowScrollOffset: Int = 0,
    initialColumnsSizeInPx: Map<Int, Float> = emptyMap()
): TableState = rememberSaveable(saver = TableState.Saver) {
    TableState(
        initialFirstVisibleRowIndex = initialFirstVisibleRowIndex,
        initialFirstVisibleRowScrollOffset = initialFirstVisibleRowScrollOffset,
        initialColumnsSizeInPx = initialColumnsSizeInPx
    )
}

@Composable
fun <T> TableView(
    tableData: Table<T>,
    modifier: Modifier = Modifier,
    dividerWidth: Dp = 1.dp,
    state: TableState = rememberTableState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    content: @Composable (T, Int, Int) -> Unit
) {
    if (tableData.isEmpty()) return

    val dividerWidthPx = with(LocalDensity.current) { dividerWidth.toPx() }

    BoxWithConstraints(modifier = modifier) {
        val tableWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        state.initializeState(tableWidthPx, dividerWidthPx, tableData.columnsCount)
        state.onTableResize(tableWidthPx)
        LazyColumn(
            state = state.listState,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            flingBehavior = flingBehavior
        ) {
            items(tableData.rowsCount) { rowIndex ->
                Row(tableData.columnsCount, tableData, rowIndex, state, tableWidthPx, dividerWidthPx, content)
            }
        }
    }
}

@Composable
private fun <T> Row(
    columnsCount: Int,
    tableData: Table<T>,
    rowIndex: Int,
    dividersState: TableDividersState,
    tableWidthPx: Float,
    dividerWidthPx: Float,
    content: @Composable (T, Int, Int) -> Unit
) {
    Layout(
        content = {
            for (columnIndex in 0 until columnsCount) {
                val cellId = TableLayoutId.Cell(columnIndex, columnIndex == columnsCount - 1)

                Box(modifier = Modifier.layoutId(cellId)) {
                    content(tableData[rowIndex, columnIndex], rowIndex, columnIndex)
                }

                if (columnIndex != columnsCount - 1) {
                    val dividerId = TableLayoutId.Divider
                    Box(
                        modifier = Modifier.layoutId(dividerId)
                            .fillMaxSize()
                            .background(Color.Green) // TODO theming
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta -> dividersState.updateDividerOffset(columnIndex, delta) }
                            )
                    )
                }
            }
        },
        measurePolicy = { measurables, constraints ->
            var maxHeightPx = 0
            var sparePixels = 0f

            val measures = measurables.map { measurable ->
                val layoutId = measurable.layoutId

                val rawElementWidth = when (layoutId) {
                    is TableLayoutId.Cell -> when (layoutId.cellIndex) {
                        0 -> dividersState.getDividerOffset(0)
                        columnsCount - 1 -> tableWidthPx - dividersState.getDividerOffset(layoutId.cellIndex - 1) - dividerWidthPx
                        else -> dividersState.getDividerOffset(layoutId.cellIndex) - dividersState.getDividerOffset(layoutId.cellIndex - 1) - dividerWidthPx
                    }
                    is TableLayoutId.Divider -> dividerWidthPx
                    else -> error("Unknown layoutId $layoutId")
                }

                val roundedElementWidth = rawElementWidth.toInt()

                sparePixels += rawElementWidth - roundedElementWidth

                val finalWidth = when (layoutId) {
                    is TableLayoutId.Cell -> when {
                        sparePixels >= 1 && !layoutId.isLast -> {
                            sparePixels -= 1f
                            roundedElementWidth + 1
                        }
                        layoutId.isLast -> {
                            roundedElementWidth + sparePixels.roundToInt()
                        }
                        else -> roundedElementWidth
                    }
                    else -> roundedElementWidth
                }

                val measuredHeightPx = measurable.maxIntrinsicHeight(finalWidth)
                maxHeightPx = max(maxHeightPx, measuredHeightPx)
                measurable.withWidth(finalWidth)
            }

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
    )
}

private data class MeasurableWithWidth(val measurable: Measurable, val width: Int)

private fun Measurable.withWidth(width: Int) = MeasurableWithWidth(this, width)

private sealed class TableLayoutId {

    object Divider : TableLayoutId()
    data class Cell(val cellIndex: Int, val isLast: Boolean) : TableLayoutId()
}
