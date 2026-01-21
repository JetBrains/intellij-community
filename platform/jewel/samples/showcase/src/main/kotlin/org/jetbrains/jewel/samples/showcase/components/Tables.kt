// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.lazy.selectable.selectionManager
import org.jetbrains.jewel.foundation.lazy.table.LazyTable
import org.jetbrains.jewel.foundation.lazy.table.LazyTableScope
import org.jetbrains.jewel.foundation.lazy.table.draggable.lazyTableCellDraggingOffset
import org.jetbrains.jewel.foundation.lazy.table.draggable.lazyTableDraggable
import org.jetbrains.jewel.foundation.lazy.table.draggable.lazyTableDraggableRowCell
import org.jetbrains.jewel.foundation.lazy.table.draggable.rememberLazyTableColumnDraggingState
import org.jetbrains.jewel.foundation.lazy.table.draggable.rememberLazyTableRowDraggingState
import org.jetbrains.jewel.foundation.lazy.table.rememberLazyTableState
import org.jetbrains.jewel.foundation.lazy.table.rememberTableHorizontalScrollbarAdapter
import org.jetbrains.jewel.foundation.lazy.table.rememberTableVerticalScrollbarAdapter
import org.jetbrains.jewel.foundation.lazy.table.selectable.rememberSingleCellSelectionManager
import org.jetbrains.jewel.foundation.lazy.table.selectable.rememberSingleColumnSelectionManager
import org.jetbrains.jewel.foundation.lazy.table.selectable.rememberSingleRowSelectionManager
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.EmptyCell
import org.jetbrains.jewel.ui.component.HorizontalScrollbar
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.SimpleTextTableViewCell
import org.jetbrains.jewel.ui.component.SimpleTextTableViewHeaderCell
import org.jetbrains.jewel.ui.component.TableViewCell
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun Tables(modifier: Modifier = Modifier) {
    Column(modifier) {
        Divider(Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
        AnimatedContent(
            targetState = TableSampleViewModel.currentSample,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize().padding(top = 16.dp),
        ) {
            it.content()
        }
    }
}

@Composable
internal fun SimpleTable(modifier: Modifier = Modifier) {
    var selectionStateIndex by remember { mutableIntStateOf(0) }
    val buttons by remember {
        derivedStateOf {
            listOf("Prefetch Items", "Cache Window").mapIndexed { index, title ->
                SegmentedControlButtonData(
                    selected = index == selectionStateIndex,
                    content = { Text(title) },
                    onSelect = { selectionStateIndex = index },
                )
            }
        }
    }

    val content = remember { List(100) { row -> List(100) { column -> "($row, $column)" } } }

    Column(modifier.fillMaxSize()) {
        Text("Prefetch Strategy")
        SegmentedControl(buttons = buttons, modifier = Modifier.padding(top = 4.dp))

        AnimatedContent(selectionStateIndex, modifier = Modifier.padding(top = 16.dp)) {
            val state =
                when (it) {
                    1 ->
                        rememberLazyTableState(
                            cacheWindow = LazyLayoutCacheWindow(aheadFraction = 0.5f, behindFraction = 0.5f)
                        )

                    else -> rememberLazyTableState()
                }

            Box(Modifier.fillMaxSize()) {
                LazyTable(state = state, modifier = Modifier.fillMaxSize()) {
                    columns(count = 100, size = { LazyTableScope.ColumnSize.Fixed(64.dp) })

                    rows(100, size = { LazyTableScope.RowSize.Fixed(24.dp) }) { rowIndex ->
                        cells(100) { columnIndex ->
                            SimpleTextTableViewCell(text = content[rowIndex][columnIndex], alignment = Alignment.Center)
                        }
                    }
                }

                HorizontalScrollbar(
                    scrollState = { state.horizontalScrollableState },
                    adapter = { rememberTableHorizontalScrollbarAdapter(state) },
                    modifier = Modifier.align(Alignment.BottomStart),
                )
                VerticalScrollbar(
                    scrollState = { state.verticalScrollableState },
                    adapter = { rememberTableVerticalScrollbarAdapter(state) },
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}

@Composable
internal fun SelectableTable(modifier: Modifier = Modifier) {
    var selectionStateIndex by remember { mutableIntStateOf(0) }
    val buttons by remember {
        derivedStateOf {
            listOf("Single Cell", "Single Row", "Single Column").mapIndexed { index, title ->
                SegmentedControlButtonData(
                    selected = index == selectionStateIndex,
                    content = { Text(title) },
                    onSelect = { selectionStateIndex = index },
                )
            }
        }
    }

    val content = remember { List(100) { row -> List(100) { column -> "($row, $column)" } } }

    Column(modifier.fillMaxSize()) {
        Text("Selection Mode")
        SegmentedControl(buttons = buttons, modifier = Modifier.padding(top = 4.dp))

        AnimatedContent(
            selectionStateIndex,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { currentSelectionType ->
            val state = rememberLazyTableState()
            val selectionState =
                when (currentSelectionType) {
                    1 -> rememberSingleRowSelectionManager()
                    2 -> rememberSingleColumnSelectionManager()
                    else -> rememberSingleCellSelectionManager()
                }

            Column {
                Row { Text("Current Selection: ${selectionState.selectedItems}") }

                Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
                    LazyTable(state = state, modifier = Modifier.fillMaxSize().selectionManager(selectionState)) {
                        columns(count = 100, size = { LazyTableScope.ColumnSize.Fixed(64.dp) })

                        rows(count = 100, size = { LazyTableScope.RowSize.Fixed(24.dp) }, key = { "Row $it" }) {
                            rowIndex ->
                            cells(100, key = { "Column $it" }) { columnIndex ->
                                SimpleTextTableViewCell(
                                    text = content[rowIndex][columnIndex],
                                    alignment = Alignment.Center,
                                )
                            }
                        }
                    }

                    HorizontalScrollbar(
                        scrollState = { state.horizontalScrollableState },
                        adapter = { rememberTableHorizontalScrollbarAdapter(state) },
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                    VerticalScrollbar(
                        scrollState = { state.verticalScrollableState },
                        adapter = { rememberTableVerticalScrollbarAdapter(state) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MovableContentTable(modifier: Modifier = Modifier) {
    class User(
        val id: Int,
        val username: String,
        val email: String = "$username@domain.com",
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = createdAt,
    ) {
        fun valueForColumn(index: Int): String =
            when (index) {
                0 -> id.toString()
                1 -> username
                2 -> email
                3 -> createdAt.toString()
                4 -> updatedAt.toString()
                else -> error("Invalid column index $index")
            }
    }

    val content: SnapshotStateList<User> = remember {
        List(100) { User(it + 1, "u_${Random.nextInt(1000, 9999)}") }.toMutableStateList()
    }

    val state = rememberLazyTableState()

    val draggableRowState =
        rememberLazyTableRowDraggingState(
            state,
            itemCanMove = { true },
            onMove = { from, to ->
                val fromIndex =
                    content.indexOfFirst { it.id == from }.takeIf { it >= 0 }
                        ?: return@rememberLazyTableRowDraggingState false
                val toIndex =
                    content.indexOfFirst { it.id == to }.takeIf { it >= 0 }
                        ?: return@rememberLazyTableRowDraggingState false

                println("Moving $fromIndex to $toIndex")

                content.add(toIndex, content.removeAt(fromIndex))
                true
            },
        )

    Box(modifier.fillMaxSize()) {
        LazyTable(
            state = state,
            pinnedColumns = 1,
            modifier = Modifier.fillMaxSize().lazyTableDraggable(rowDraggingState = draggableRowState),
        ) {
            columns(count = 5, size = { LazyTableScope.ColumnSize.Fixed(if (it == 2) 160.dp else 128.dp) })

            row(size = LazyTableScope.RowSize.Fixed(24.dp)) {
                cell { Text("ID", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                cell { Text("Username", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                cell { Text("Email", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                cell { Text("Created At", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                cell { Text("Updated At", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
            }

            rows(content.size, size = { LazyTableScope.RowSize.Fixed(24.dp) }, key = { content[it].id }) { rowIndex ->
                cells(5) { column ->
                    Text(
                        content[rowIndex].valueForColumn(column),
                        maxLines = 1,
                        modifier =
                            Modifier.thenIf(column == 0) { lazyTableDraggableRowCell(key) }
                                .thenIf(column > 0) { lazyTableCellDraggingOffset(key) },
                    )
                }
            }
        }

        HorizontalScrollbar(
            scrollState = { state.horizontalScrollableState },
            adapter = { rememberTableHorizontalScrollbarAdapter(state) },
            modifier = Modifier.align(Alignment.BottomStart),
        )
        VerticalScrollbar(
            scrollState = { state.verticalScrollableState },
            adapter = { rememberTableVerticalScrollbarAdapter(state) },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
internal fun InteractableTable(modifier: Modifier = Modifier) {
    data class Device(
        val name: String, // Pixel 6, Samsung Galaxy S21 Ultra, etc.
        val api: Int, // 21
        val androidVersion: String, // Android 5 ("Lollipop"), etc
        val processor: String, // ARMv8/x86/etc.
        val type: String, // Virtual/physical
        val id: String = UUID.randomUUID().toString(),
    )

    val state = rememberLazyTableState()
    val deviceList = remember {
        listOf(
                Device("Asus ROG Phone 5", 30, "Android 11", "ARMv8", "Physical"),
                Device("Galaxy Tab S7", 30, "Android 11", "ARMv8", "Physical"),
                Device("Generic x86", 28, "Android 9.0 (Pie)", "x86", "Virtual"),
                Device("Generic x86_64", 30, "Android 11", "x86_64", "Virtual"),
                Device("Huawei P40 Pro", 29, "Android 10", "ARMv8", "Physical"),
                Device("LG Wing", 29, "Android 10", "ARMv8", "Physical"),
                Device("Moto G Power", 29, "Android 10", "ARMv7", "Physical"),
                Device("Nexus 5X", 23, "Android 6.0 (Marshmallow)", "ARMv7", "Virtual"),
                Device("Nexus 6P", 24, "Android 7.0 (Nougat)", "ARMv8", "Virtual"),
                Device("OnePlus 9 Pro", 30, "Android 11", "ARMv8", "Physical"),
                Device("Oppo Find X3 Pro", 30, "Android 11", "ARMv8", "Physical"),
                Device("Pixel 2 XL", 28, "Android 9.0 (Pie)", "ARMv8", "Virtual"),
                Device("Pixel 3a XL", 29, "Android 10", "ARMv8", "Virtual"),
                Device("Pixel 4a", 30, "Android 11", "ARMv8", "Virtual"),
                Device("Pixel 5", 30, "Android 11", "ARMv8", "Virtual"),
                Device("Pixel 6", 31, "Android 12", "ARMv8", "Physical"),
                Device("Samsung Galaxy S21 Ultra", 30, "Android 11", "ARMv8", "Physical"),
                Device("Sony Xperia 5 III", 30, "Android 11", "ARMv8", "Physical"),
                Device("Vivo X60 Pro", 30, "Android 11", "ARMv8", "Physical"),
                Device("Xiaomi Mi 11", 30, "Android 11", "ARMv8", "Physical"),
            )
            .toMutableStateList()
    }

    var editingEntry by remember { mutableStateOf<String?>(null) }

    Box(modifier.fillMaxSize()) {
        LazyTable(state = state, modifier = Modifier.fillMaxSize(), pinnedRows = 1) {
            column(size = LazyTableScope.ColumnSize.Percent(0.6f)) // Name
            column(size = LazyTableScope.ColumnSize.Percent(0.15f)) // API
            column(size = LazyTableScope.ColumnSize.Percent(0.15f)) // Type
            column(size = LazyTableScope.ColumnSize.Percent(0.1f)) // Type

            // Header
            row(size = LazyTableScope.RowSize.Fixed(32.dp)) {
                cell {
                    SimpleTextTableViewHeaderCell(
                        "Name",
                        fontWeight = FontWeight.Bold,
                        alignment = Alignment.CenterStart,
                        textModifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                cell {
                    SimpleTextTableViewHeaderCell(
                        "API",
                        fontWeight = FontWeight.Bold,
                        alignment = Alignment.CenterStart,
                        textModifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                cell {
                    SimpleTextTableViewHeaderCell(
                        "Type",
                        fontWeight = FontWeight.Bold,
                        alignment = Alignment.CenterStart,
                        textModifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                cell { EmptyCell() }
            }

            // Devices
            rows(deviceList.size, key = { deviceList[it].id }, size = { LazyTableScope.RowSize.Fixed(42.dp) }) {
                rowIndex ->
                cell(key = "Name") {
                    val device = deviceList[rowIndex]
                    val isEditing by remember { derivedStateOf { editingEntry == device.id } }

                    TableViewCell(
                        alignment = Alignment.CenterStart,
                        modifier =
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { if (!isEditing) editingEntry = device.id })
                            },
                    ) {
                        AnimatedContent(
                            targetState = isEditing,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                        ) { editing ->
                            if (editing) {
                                val textState = rememberTextFieldState(device.name)
                                val focusRequester = remember { FocusRequester() }

                                TextField(
                                    state = textState,
                                    placeholder = { Text("Device Name") },
                                    undecorated = true,
                                    modifier =
                                        Modifier.focusRequester(focusRequester)
                                            .padding(horizontal = 8.dp)
                                            .fillMaxWidth()
                                            .onPreviewKeyEvent { event ->
                                                when (event.key) {
                                                    Key.Enter,
                                                    Key.NumPadEnter,
                                                    Key.Escape -> {
                                                        editingEntry = null
                                                        true
                                                    }

                                                    else -> {
                                                        false
                                                    }
                                                }
                                            },
                                )

                                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                                LaunchedEffect(textState.text) {
                                    deviceList[rowIndex] = deviceList[rowIndex].copy(name = textState.text.toString())
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                ) {
                                    Text(device.name)
                                    Text(
                                        "${device.androidVersion} | ${device.processor}",
                                        style =
                                            LocalTextStyle.current.let { it.copy(fontSize = it.fontSize.times(0.8f)) },
                                    )
                                }
                            }
                        }
                    }
                }

                cell(key = "Api") {
                    SimpleTextTableViewCell(
                        deviceList[rowIndex].api.toString(),
                        alignment = Alignment.CenterStart,
                        textModifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                cell(key = "Type") {
                    SimpleTextTableViewCell(
                        deviceList[rowIndex].type,
                        alignment = Alignment.CenterStart,
                        textModifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                cell(key = "Actions") {
                    TableViewCell(alignment = Alignment.Center) {
                        Row {
                            IconButton(onClick = {}) { Icon(AllIconsKeys.Actions.Execute, null) }
                            IconButton(onClick = {}) { Icon(AllIconsKeys.Actions.More, null) }
                        }
                    }
                }
            }
        }

        HorizontalScrollbar(
            scrollState = { state.horizontalScrollableState },
            adapter = { rememberTableHorizontalScrollbarAdapter(state) },
            modifier = Modifier.align(Alignment.BottomStart),
        )
        VerticalScrollbar(
            scrollState = { state.verticalScrollableState },
            adapter = { rememberTableVerticalScrollbarAdapter(state) },
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
internal fun AllFeaturesTable(modifier: Modifier = Modifier) {
    val rows = remember {
        mutableStateListOf<OrderTableRow>().apply { addAll(List(100) { OrderTableRow(Order.fake(it + 1)) }) }
    }
    var sortingState by remember { mutableStateOf<Pair<SortOrder, String>?>(null) }
    val nextId by remember { derivedStateOf { (rows.maxByOrNull { it.id }?.id ?: 0) + 1 } }

    val columns = remember {
        mutableStateListOf(
            OrderTableColumnInfo(
                title = "ID",
                size = LazyTableScope.ColumnSize.Constrained(minWidth = 60.dp),
                textProvider = Order::id,
                comparator = { l, r -> l.id.compareTo(r.id) },
            ),
            OrderTableColumnInfo(
                title = "Transaction ID",
                size = LazyTableScope.ColumnSize.Constrained(minWidth = 150.dp),
                textProvider = Order::transactionId,
                comparator = { l, r -> l.transactionId.compareTo(r.transactionId, ignoreCase = true) },
            ),
            OrderTableColumnInfo(
                title = "User ID",
                size = LazyTableScope.ColumnSize.Constrained(minWidth = 150.dp),
                textProvider = Order::uid,
                comparator = { l, r -> l.uid.compareTo(r.uid, ignoreCase = true) },
            ),
            OrderTableColumnInfo(
                title = "Product ID",
                size = LazyTableScope.ColumnSize.Constrained(minWidth = 150.dp),
                textProvider = Order::productId,
                comparator = { l, r -> l.productId.compareTo(r.productId) },
            ),
            OrderTableColumnInfo(
                title = "Product Name",
                size = LazyTableScope.ColumnSize.Constrained(minWidth = 150.dp),
                textProvider = Order::productName,
                comparator = { l, r -> l.productName.compareTo(r.productName, ignoreCase = true) },
            ),
            OrderTableColumnInfo(
                title = "Price",
                size = LazyTableScope.ColumnSize.Constrained(minWidth = 120.dp),
                textProvider = Order::price,
                comparator = { l, r -> l.price.compareTo(r.price, ignoreCase = true) },
            ),
            OrderTableColumnInfo(
                title = "Create Time",
                size = LazyTableScope.ColumnSize.Constrained(minWidth = 120.dp),
                textProvider = Order::createTime,
                comparator = { l, r -> l.createTime.compareTo(r.createTime) },
            ),
            OrderTableColumnInfo(
                title = "Update Time",
                size = LazyTableScope.ColumnSize.Constrained(minWidth = 120.dp),
                textProvider = Order::updateTime,
                comparator = { l, r -> l.updateTime.compareTo(r.updateTime) },
            ),
        )
    }

    val state = rememberLazyTableState()
    val draggableColumnState =
        rememberLazyTableColumnDraggingState(
            state,
            itemCanMove = { true },
            onMove = { from, to ->
                val fromIndex =
                    columns.indexOfFirst { it.title == from }.takeIf { it >= 0 }
                        ?: return@rememberLazyTableColumnDraggingState false
                val toIndex =
                    columns.indexOfFirst { it.title == to }.takeIf { it >= 0 }
                        ?: return@rememberLazyTableColumnDraggingState false

                // Do not allow replacing the fixed column
                if (toIndex == 0) return@rememberLazyTableColumnDraggingState false

                columns.add(toIndex, columns.removeAt(fromIndex))
                true
            },
        )
    val draggableRowState =
        rememberLazyTableRowDraggingState(
            state,
            itemCanMove = { true },
            onMove = { from, to ->
                val fromIndex =
                    rows.indexOfFirst { it.id == from }.takeIf { it >= 0 }
                        ?: return@rememberLazyTableRowDraggingState false
                val toIndex =
                    rows.indexOfFirst { it.id == to }.takeIf { it >= 0 }
                        ?: return@rememberLazyTableRowDraggingState false

                rows.add(toIndex, rows.removeAt(fromIndex))
                true
            },
        )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { rows.add(OrderTableRow(Order.fake(nextId))) }) { Text("Add row") }

            OutlinedButton(onClick = { rows.clear() }) { Text("Clear") }

            OutlinedButton(
                onClick = {
                    rows.clear()
                    rows.addAll(List(100) { OrderTableRow(Order.fake(it + 1)) })
                }
            ) {
                Text("Init")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val row = rememberTextFieldState()

            TextField(state = row, placeholder = { Text("Row ID") })

            OutlinedButton(
                onClick = {
                    val id = row.text.toString().toIntOrNull()
                    val index = rows.indexOfFirst { it.id == id }

                    if (index >= 0) {
                        rows.removeAt(index)
                    }
                }
            ) {
                Text("Remove row")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val column = rememberTextFieldState()
            val row = rememberTextFieldState()

            TextField(state = column, placeholder = { Text("Column") })

            TextField(state = row, placeholder = { Text("Row") })

            val coroutine = rememberCoroutineScope()

            OutlinedButton(
                onClick = {
                    coroutine.launch {
                        state.scrollToColumn(column.text.toString().toIntOrNull() ?: 0)
                        state.scrollToRow(row.text.toString().toIntOrNull() ?: 0)
                    }
                }
            ) {
                Text("Goto")
            }

            OutlinedButton(
                onClick = {
                    coroutine.launch { state.animateScrollToColumn(column.text.toString().toIntOrNull() ?: 0) }
                    coroutine.launch { state.animateScrollToRow(row.text.toString().toIntOrNull() ?: 0) }
                }
            ) {
                Text("Goto with Animation")
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyTable(
                modifier =
                    Modifier.lazyTableDraggable(draggableRowState, draggableColumnState)
                        .selectionManager(rememberSingleRowSelectionManager()),
                state = state,
                pinnedColumns = 1,
                pinnedRows = 1,
            ) {
                columns(count = columns.size, key = { columns[it].title }, size = { columns[it].size })

                row(size = LazyTableScope.RowSize.Constrained(24.dp), key = "TableHeader") {
                    cells(columns.size) { index ->
                        val column = columns[index]
                        SimpleTextTableViewHeaderCell(
                            text = column.title,
                            alignment = Alignment.Center,
                            maxLines = 1,
                            trailingIcon =
                                if (column.title == sortingState?.second) {
                                    { sortingState?.first?.Icon() }
                                } else {
                                    null
                                },
                            onResizeWidth = { updatedWidth ->
                                columns[index] =
                                    columns[index].copy(size = LazyTableScope.ColumnSize.Fixed(updatedWidth))
                            },
                            textModifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            modifier =
                                Modifier.clickable {
                                    if (column.comparator == null && column.reverseComparator == null) return@clickable

                                    val order =
                                        if (column.title == sortingState?.second) {
                                            sortingState?.first?.toggle() ?: SortOrder.ASCENDING
                                        } else {
                                            if (column.comparator != null) {
                                                SortOrder.ASCENDING
                                            } else {
                                                SortOrder.DESCENDING
                                            }
                                        }

                                    val comparator = column.comparator(order)
                                    if (comparator == null) return@clickable

                                    sortingState = order to column.title
                                    rows.sortWith { l, r -> comparator.compare(l.order, r.order) }
                                },
                        )
                    }
                }

                rows(rows.size, key = { rows[it].id }, size = { rows[it].size }) { rowIndex ->
                    cells(columns.size, key = { columns[it].title }) { cellIndex ->
                        SimpleTextTableViewCell(
                            text = columns[cellIndex].textProvider(rows[rowIndex].order).toString(),
                            alignment = if (cellIndex == 0) Alignment.CenterEnd else Alignment.CenterStart,
                            textModifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            onResizeWidth = { updatedWidth ->
                                columns[cellIndex] =
                                    columns[cellIndex].copy(size = LazyTableScope.ColumnSize.Fixed(updatedWidth))
                            },
                            onResizeHeight = { updatedHeight ->
                                rows[rowIndex] = rows[rowIndex].copy(size = LazyTableScope.RowSize.Fixed(updatedHeight))
                            },
                        )
                    }
                }
            }

            HorizontalScrollbar(
                scrollState = { state.horizontalScrollableState },
                adapter = { rememberTableHorizontalScrollbarAdapter(state) },
                modifier = Modifier.align(Alignment.BottomStart),
            )
            VerticalScrollbar(
                scrollState = { state.verticalScrollableState },
                adapter = { rememberTableVerticalScrollbarAdapter(state) },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

private data class Order(
    val id: Int,
    val transactionId: String,
    val uid: String,
    val productId: Int,
    val productName: String,
    val price: String,
    val shippingAddress: String,
    val postalCode: String,
    val createTime: Int,
    val updateTime: Int,
) {
    companion object {
        val random = Random.Default

        fun fake(id: Int): Order =
            Order(
                id = id,
                transactionId = "T${random.nextInt().toString().take(8)}",
                uid = "U_${random.nextUInt().toString().take(8)}",
                productId = random.nextInt(65535),
                productName = "Product $id abcdefgh $id 12345678 $id",
                price = random.nextInt(10000).toString(),
                shippingAddress = "1600 Amphitheatre Parkway",
                postalCode = "94043",
                createTime = 0,
                updateTime = 0,
            )
    }
}

private data class OrderTableRow(
    val order: Order,
    val size: LazyTableScope.RowSize = LazyTableScope.RowSize.Constrained(24.dp),
) {
    val id: Int
        get() = order.id
}

private data class OrderTableColumnInfo(
    val title: String,
    val size: LazyTableScope.ColumnSize,
    val textProvider: (Order) -> Any?,
    val comparator: Comparator<Order>? = null,
    val reverseComparator: Comparator<Order>? = comparator?.reversed(),
) {
    fun comparator(order: SortOrder): Comparator<Order>? =
        when (order) {
            SortOrder.ASCENDING -> comparator
            SortOrder.DESCENDING -> reverseComparator
        }
}

private enum class SortOrder {
    ASCENDING,
    DESCENDING;

    @Composable
    fun Icon(modifier: Modifier = Modifier) {
        Icon(
            when (this) {
                ASCENDING -> AllIconsKeys.General.ArrowUp
                DESCENDING -> AllIconsKeys.General.ArrowDown
            },
            contentDescription = null,
            modifier = modifier.size(16.dp),
        )
    }

    fun toggle(): SortOrder =
        when (this) {
            ASCENDING -> DESCENDING
            DESCENDING -> ASCENDING
        }
}

@Composable
internal fun TablesMenu(modifier: Modifier = Modifier) {
    ListComboBox(
        modifier = modifier.widthIn(max = 200.dp),
        items = TableSampleViewModel.allSamples,
        selectedIndex = TableSampleViewModel.selectedIndex,
        onSelectedItemChange = { TableSampleViewModel.selectedIndex = it },
        itemKeys = { _, item -> item },
        itemContent = { item, isSelected, isActive ->
            SimpleListItem(text = item.title, selected = isSelected, active = isActive)
        },
    )
}

internal object TableSampleViewModel {
    val allSamples =
        listOf(
            TabSampleCase("Simple Table") { SimpleTable() },
            TabSampleCase("Selectable Table") { SelectableTable() },
            TabSampleCase("Movable Content Table") { MovableContentTable() },
            TabSampleCase("Interactable Table") { InteractableTable() },
            TabSampleCase("All Features Table") { AllFeaturesTable() },
        )

    var selectedIndex by mutableIntStateOf(0)
    val currentSample by derivedStateOf { allSamples[selectedIndex] }

    data class TabSampleCase(val title: String, val content: @Composable () -> Unit)
}
