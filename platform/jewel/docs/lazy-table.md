# Lazy Table

This documentation is a short guidance on how to use the Lazy Table from Jewel.

The entrypoint is the `org.jetbrains.jewel.foundation.lazy.table.LazyTable` component. It's a foundation component,
therefore, it doesn't have any styles. The main purpose of this component is to provide a way to load data lazily and
support other features like sorting, dragging, selecting, etc..

## Basic usage

A basic usage you can think of is a matrix of data. To simulate this scenario, we will use the `List` constructor to
generate the matrix with a size of 100x100.

```kotlin
val MATRIX_SIZE = 100

@Composable
fun MyComposable() {
  val content = remember { List(MATRIX_SIZE) { row -> List(MATRIX_SIZE) { column -> "($row, $column)" } } }
}
```

Then, to render the table, you will call the component and configure it using the trailing lambda, similar to the
`LazyColumn`/`LazyRow` component.

In that lambda, you need to configure two different things:

1. The columns: When calling the `column` (or `columns`) lambda, you define the number of columns that will be displayed
   to the user. You can also define other properties that will be used to that column, like the size, lazy key, etc.
2. The rows: When calling the `row` (or `rows`) lambda, you define the number of rows that will be displayed to the
   user.
   Inside that lambda, you can define the cells for each row.

```kotlin
val MATRIX_SIZE = 100

@Composable
fun MyComposable() {
  val content = remember { List(MATRIX_SIZE) { row -> List(MATRIX_SIZE) { column -> "($row, $column)" } } }

  LazyTable {
    // 1. Define the columns
    columns(count = MATRIX_SIZE, size = { LazyTableScope.ColumnSize.Fixed(64.dp) })

    // 2. Define the rows
    rows(content.size, size = { LazyTableScope.RowSize.Fixed(24.dp) }) { rowIndex ->
      // 2.1 Define each cell that will be rendered for that row
      cells(MATRIX_SIZE) { columnIndex ->
        Text(text = content[rowIndex][columnIndex])
      }
    }
  }
}
```

With that, you should be able to see a table with the data from the matrix.

## Features

### Scrollbars

Scroll on both axis are supported by default by the component. However, the component will not render scrollbars.
If you want to render scrollbars, you can use the `HorizontalScrollbar`/`VerticalScrollbar` components from Jewel with
the table state.

```kotlin
@Composable
internal fun SimpleTable(modifier: Modifier = Modifier) {
  val content = remember { List(100) { row -> List(100) { column -> "($row, $column)" } } }
  val state = rememberLazyTableState() // 1. Create the table state

  Box(modifier.fillMaxSize()) { // 2. Wrap the table in a Box
    LazyTable(state = state, modifier = Modifier.fillMaxSize()) {
      columns(count = 100, size = { LazyTableScope.ColumnSize.Fixed(64.dp) })

      rows(content.size, size = { LazyTableScope.RowSize.Fixed(24.dp) }) { rowIndex ->
        cells(100) { columnIndex -> Text(text = content[rowIndex][columnIndex]) }
      }
    }

    // 3. Render the scrollbars using scroll adapters
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

```

### Pinned Columns and Rows

Pinned Columns and Rows is a feature that allows you to define columns and rows that will always be visible.
You can `pinnedColumns`/`pinnedRows` parameters to define how many columns/rows will be pinned.

```kotlin
val MATRIX_SIZE = 100

@Composable
fun MyComposable() {
  val content = remember { List(MATRIX_SIZE) { row -> List(MATRIX_SIZE) { column -> "($row, $column)" } } }

  LazyTable(
    pinnedColumns = 1, // Only the first column will be pinned
    pinnedRows = 1, // Only the first row will be pinned
  ) {
    columns(count = MATRIX_SIZE, size = { LazyTableScope.ColumnSize.Fixed(64.dp) })

    rows(content.size, size = { LazyTableScope.RowSize.Fixed(24.dp) }) { rowIndex ->
      cells(MATRIX_SIZE) { columnIndex -> Text(text = content[rowIndex][columnIndex]) }
    }
  }
}

```

### Selection

To enable cell selection, you need do configure your table and cells to "read" manage the selection. This is done by:

1. Setting the `selectionManager` modifier to the table
1. You can use any one of the available selection managers: Single Cell, Single Row, or Single Column; More can be
   added as needed.
2. Setting the `selectableCell` modifier to the cell
1. When setting it, you can use the `key` provided in the `LazyTableCellScope` like shown bellow
3. Use the `onSelectChanged` to track the selection state change inside the cell

To provide the selection managers, we have a few helper functions that you can use in the `compose` scope:

- `rememberSingleCellSelectionManager()`
- `rememberSingleRowSelectionManager()`
- `rememberSingleColumnSelectionManager()`

That class has a property called `selectionState.selectedItems`, which can be used to get the selected items. It's using
the compose `State` under the hood, so you can use it in any composable to track the selection.

```kotlin
val MATRIX_SIZE = 100

@Composable
fun MyComposable() {
  val content = remember { List(MATRIX_SIZE) { row -> List(MATRIX_SIZE) { column -> "($row, $column)" } } }
  val selectionState = rememberSingleRowSelectionManager()

  LazyTable(
    modifier = Modifier.selectionManager(selectionState) // 1. Define the selection manager
  ) {
    columns(count = MATRIX_SIZE, size = { LazyTableScope.ColumnSize.Fixed(64.dp) })

    rows(content.size, size = { LazyTableScope.RowSize.Fixed(24.dp) }, key = { "Row $it" }) { rowIndex ->
      cells(MATRIX_SIZE, key = { "Column $it" }) { columnIndex ->
        var isSelected by remember { mutableStateOf(false) }
        Text(
          text = content[rowIndex][columnIndex],
          color = if (isSelected) Color.Red else Color.Black, // Use the selected state to change the style
          modifier =
            Modifier
              // 2. Define the cell as selectable
              .selectableCell(key)
              // 3. Track the selection state change
              .onSelectChanged(key) { isSelected = it },
        )
      }
    }
  }

  LaunchedEffect(selectionState.selectedItems) { println("Current selection is: ${selectionState.selectedItems}") }
}

```

The selected items return a set of `Pair<Any?, Any?>`. The Pair contains the keys of the row and column that are
selected.
The first item is the column key, and the second item is the row key. You can define them in the DSL `key` parameters.

Note that the column key can be defined in either the `columns` or the `cells` lambda. If you define in both, the value
set in the `cell` will be used.

### Draggable columns and rows

Enabling drag and drop in the table is a bit more complex than the other features, so you have to take some extra steps
into consideration.

1. The drag action can only be performed on "pinned cells".
1. For that, you must set the `pinnedColumns` to be at least `= 1` to move the rows; Or the `pinnedRows` to be at
   least `= 1` to move the columns;
2. Even though you can add the "drag modifiers" in the cells that are not pinned, it will not call any of the
   callbacks;
2. The cells that are not pinned, you will need to add a `dragOffset` modifier so they can follow the movement of the
   table.
3. Similarly to selection, you need to also update the table modifier to include the dragging state manager.
4. Just like the selection, the dragging relies on the `key` parameter to identify the cells and return the callbacks.

In this sample, we will use a list of "Users" to simulate the table content. For that, consider the following class:

```kotlin
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
```

Now, let's implement the draggable table:

```kotlin
@Composable
fun DraggableRowsTable(modifier: Modifier = Modifier) {
  // 1. Create a mutable list of users
  val content = remember {
    List(100) { User(it + 1, "u_${Random.nextInt(1000, 9999)}") }
      .toMutableStateList()
  }

  val state = rememberLazyTableState()

  // 2. Create the dragging state for rows
  val draggableRowState = rememberLazyTableRowDraggingState(
    state,
    itemCanMove = { true }, // Allow all items to be moved
    onMove = { from, to ->
      // Find the indices based on the row keys (user IDs)
      val fromIndex = content.indexOfFirst { it.id == from }
                        .takeIf { it >= 0 } ?: return@rememberLazyTableRowDraggingState false
      val toIndex = content.indexOfFirst { it.id == to }
                      .takeIf { it >= 0 } ?: return@rememberLazyTableRowDraggingState false

      // Perform the actual move in the content list
      content.add(toIndex, content.removeAt(fromIndex))
      true // Return true to indicate the move was successful
    },
  )

  LazyTable(
    state = state,
    pinnedColumns = 1, // 3. Required: At least 1 pinned column for row dragging
    // 4. Add the draggable modifier to the table
    modifier = modifier.fillMaxSize().lazyTableDraggable(rowDraggingState = draggableRowState),
  ) {
    columns(count = 5, size = { LazyTableScope.ColumnSize.Fixed(128.dp) })

    // 5. Define rows with keys
    rows(
      content.size,
      size = { LazyTableScope.RowSize.Fixed(24.dp) },
      key = { content[it].id } // Important: provide stable keys
    ) { rowIndex ->
      cells(5) { columnIndex ->
        Text(
          content[rowIndex].valueForColumn(columnIndex),
          modifier = Modifier
            // 6. First column: make it the drag handle
            .thenIf(columnIndex == 0) { lazyTableDraggableRowCell(key) }
            // 7. Other columns: add offset to follow the drag
            .thenIf(columnIndex > 0) { lazyTableCellDraggingOffset(key) }
        )
      }
    }
  }
}
```

**Key points:**

1. **Mutable List**: Use a `SnapshotStateList` (via `toMutableStateList()` or `mutableStateListOf`) so changes trigger
   recomposition
2. **Dragging State**: Create with `rememberLazyTableRowDraggingState()` and provide:

- `itemCanMove`: Lambda to determine if an item can be moved (return `true` to allow all items)
- `onMove`: Lambda that receives the `from` and `to` keys and performs the actual reordering

3. **Pinned Columns**: Set `pinnedColumns = 1` or more - this is required for row dragging
4. **Table Modifier**: Add `.lazyTableDraggable(rowDraggingState = draggableRowState)` to enable dragging
5. **Row Keys**: Provide stable keys in the `rows()` function (e.g., user IDs)
6. **Drag Handle**: Use `.lazyTableDraggableRowCell(key)` on the pinned column cell to make it draggable
7. **Following Cells**: Use `.lazyTableCellDraggingOffset(key)` on non-pinned cells so they follow the drag

**Note on Column Dragging:**

To enable column dragging instead (or in addition), use `rememberLazyTableColumnDraggingState()` and set
`pinnedRows = 1` or more. The pattern is similar, but you'll need to manage column order in your state.

### Theming

While `LazyTable` is a foundation component without styling, Jewel provides themed cell and header components in
`org.jetbrains.jewel.ui.component` that integrate with the theme system and provide a complete table experience.

#### Themed Components

Jewel provides four main themed components for building styled tables:

**1. `SimpleTextTableViewCell`** - The most commonly used component for displaying text in table cells:

```kotlin
@Composable
fun StyledTable(modifier: Modifier = Modifier) {
  val content = remember { List(100) { row -> List(100) { column -> "($row, $column)" } } }
  val state = rememberLazyTableState()

  LazyTable(state = state, modifier = modifier) {
    columns(count = 100, size = { LazyTableScope.ColumnSize.Fixed(64.dp) })

    rows(content.size, size = { LazyTableScope.RowSize.Fixed(24.dp) }) { rowIndex ->
      cells(100) { columnIndex ->
        // Uses the current theme's TableStyle
        SimpleTextTableViewCell(
          text = content[rowIndex][columnIndex],
          alignment = Alignment.Center,
        )
      }
    }
  }
}
```

**2. `SimpleTextTableViewHeader`** - For styled header rows:

```kotlin
@Composable
fun TableWithHeader(modifier: Modifier = Modifier) {
  val state = rememberLazyTableState()

  LazyTable(state = state, pinnedRows = 1, modifier = modifier) {
    columns(count = 3, size = { LazyTableScope.ColumnSize.Fixed(128.dp) })

    // Header row
    row(size = LazyTableScope.RowSize.Fixed(24.dp)) {
      cell {
        SimpleTextTableViewHeader(
          text = "Name",
          fontWeight = FontWeight.Bold,
          alignment = Alignment.Center,
        )
      }
      cell {
        SimpleTextTableViewHeader(
          text = "Email",
          fontWeight = FontWeight.Bold,
          alignment = Alignment.Center,
        )
      }
      cell {
        SimpleTextTableViewHeader(
          text = "Created",
          fontWeight = FontWeight.Bold,
          alignment = Alignment.Center,
        )
      }
    }

    // Data rows...
  }
}
```

**3. `TableViewCell`** - Generic themed container for custom cell content:

```kotlin
@Composable
fun LazyTableCellScope.CustomCell(modifier: Modifier = Modifier) {
  TableViewCell(
    alignment = Alignment.Center,
    modifier = modifier,
  ) {
    // Any custom composable content
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      Icon(AllIconsKeys.General.Add, contentDescription = null)
      Text("Custom Content")
    }
  }
}
```

**4. `TableViewHeader`** - Generic themed container for custom header content (similar to `TableViewCell` but for
headers).

#### Built-in Features

These Jewel-specific components provide several features out of the box:

1. **Automatic Theming**: Uses the current `TableStyle` from `JewelTheme.tableStyle` for colors, borders, and metrics
2. **Selection Support**: Automatically handles selection visuals (background and text colors change when selected)

- Control with the `selectable` parameter (default: `true` for cells, `false` for headers)

3. **Focus and Hover States**: Shows appropriate visual feedback for focused and hovered cells
4. **Drag-and-Drop Integration**: Automatically applies the correct drag modifiers based on cell position:

- Pinned column cells become row drag handles
- Pinned row cells become column drag handles
- Non-pinned cells automatically offset during drag operations
- Control with the `moveable` parameter (default: `true` for both cells and headers)

5. **Resize Handles**: Optional `onResizeWidth` and `onResizeHeight` parameters add interactive resize handles

- More details in the 'Column and Row Resizing' section

#### Column and Row Resizing

Both columns and rows support interactive resizing via drag handles in the Jewel-provided cell components:

```kotlin
@Composable
fun ResizableTable(modifier: Modifier = Modifier) {
  val columnWidths = remember { mutableStateListOf(*Array(5) { 128.dp }) }
  val rowHeights = remember { mutableStateListOf(*Array(100) { 24.dp }) }

  LazyTable(modifier = modifier, pinnedRows = 1) {
    columns(count = 5, size = { LazyTableScope.ColumnSize.Fixed(columnWidths[it]) })

    // Resizable header
    row(size = LazyTableScope.RowSize.Fixed(24.dp)) {
      cells(5) { columnIndex ->
        SimpleTextTableViewHeader(
          text = "Column $columnIndex",
          alignment = Alignment.Center,
          // When user drags the resize handle, update the column width
          onResizeWidth = { updatedWidth ->
            columnWidths[columnIndex] = updatedWidth
          },
        )
      }
    }

    // Resizable rows
    rows(100, size = { LazyTableScope.RowSize.Fixed(rowHeights[it]) }) { rowIndex ->
      cells(5) { columnIndex ->
        SimpleTextTableViewCell(
          text = "Cell ($rowIndex, $columnIndex)",
          alignment = Alignment.Center,
          // Update both width and height
          onResizeWidth = { columnWidths[columnIndex] = it },
          onResizeHeight = { rowHeights[rowIndex] = it },
        )
      }
    }
  }
}
```

#### Customization

You can customize the appearance using the standard Compose text parameters:

```kotlin
SimpleTextTableViewCell(
  text = "Custom Style",
  color = Color.Blue,
  fontWeight = FontWeight.Bold,
  fontSize = 14.sp,
  textAlign = TextAlign.End,
  overflow = TextOverflow.Ellipsis,
  maxLines = 2,
)
```

Or provide leading/trailing icons:

```kotlin
SimpleTextTableViewCell(
  text = "With Icons",
  leadingIcon = { Icon(AllIconsKeys.General.Add, contentDescription = null) },
  trailingIcon = { Icon(AllIconsKeys.General.ArrowDown, contentDescription = null) },
)
```

## Feature Requirements

| Priority | Feature Name                     | Description                                                                                  | Status                | Implementation Details                                                                                                                                                                                               |
|----------|----------------------------------|----------------------------------------------------------------------------------------------|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| P1       | Lazy layout                      | Lazy layouts on vertical axis for performance with large datasets                            | Done                  |                                                                                                                                                                                                                      |                                                                                                                                                                                                     |
| P1       | Weighted column width            | Support for setting column widths explicitly, based on text size, or by weight               | Done                  | Implemented in `LazyTableScope.kt` lines 88-126: `ColumnSize.Fixed`, `ColumnSize.Constrained`, `ColumnSize.Percent`. Note that constrained size is based in the header component to keep the "lazyness" of the table |
| P1       | Headers                          | Configurable number of header rows with pinned row support                                   | Done                  | Implemented via `pinnedRows`/`pinnedColumns` parameter in `LazyTable.kt`; Styles provided by the custom cell components that use IntUI theme. sample usage in `Tables.kt`                                            |
| P1       | Scrolling                        | 2D scrolling support                                                                         | Done                  | Implemented in `LazyTableState.kt` (vertical scrollable state), `LazyTableScrollbarAdapter.kt` (scrollbar adapters)                                                                                                  |
| P1       | Sortable columns                 | Higher-level data model that allows defining sort order for columns                          | Partially implemented | Sample implementation in `Tables.kt` lines 314-533 with comparators and sort order toggling; however it needs manual configuration;                                                                                  |
| P1       | Keyboard navigation              | Support for keyboard navigation through cells, with focus and sort order toggling in headers | Not implemented       | Needs to improve the selectino managers to allow navigating the content                                                                                                                                              |
| P2       | Manually-resizable columns       | Interactive resize handles via drag for both columns and rows                                | Partially implemented | Same as sorting. Needs manual configuration and can be implemented via `onResizeWidth` and `onResizeHeight` callbacks in themed components; sample in `Tables.kt`                                                    |
| P2       | Styling                          | Jewel-native themed components with TableStyle integration                                   | Done                  | Themed components in `org.jetbrains.jewel.ui.component`: `SimpleTextTableViewCell`, `SimpleTextTableViewHeader`, `TableViewCell`, `TableViewHeader`                                                                  |
| P2       | Configurable spacing             | Arrangement-based spacing configuration between rows and columns                             | Done                  | Implemented via `RowSize` and `ColumnSize` in `LazyTableScope.kt`. Needs to verify usage with themed components as it was only tested with style-less components                                                     |
| P2       | Embedded interactive components  | Support for interactive components like checkboxes within cells                              | Partially implemented | `TableViewCell` accepts any composable content as documented in lines, but they gets focused on keyboard navigation.                                                                                                 |
| P2       | Expose contents on hover         | Hovering over a cell exposes entire contents using z-order                                   | Done                  | Implemented using the "OverflowBox"                                                                                                                                                                                  |
| P2       | Speed search                     | Quick textual searching similar to TableSpeedSearch with scroll-to-cell capability           | Not implemented       | Speed search components exist for other widgets (`SpeedSearchableComboBox.kt`, `SpeedSearchableLazyColumn.kt`) but not yet implemented for tables                                                                    |
| P3       | Content-responsive column sizing | Automatic column sizing based on content                                                     | Not implemented       | By design, lazy rendering is incompatible with content-responsive sizing as noted in documentation. However, we support constraints based on the header columns/rows                                                 |
| P3       | Animation                        | Animated scrolling to particular rows/columns for programmatic navigation                    | Partially implemented | Implemented in `LazyTableAnimateScroll.kt`: `animateScrollToRow()` and `animateScrollToColumn()` functions; Still missing implementation from 'LazyTableItemScope'                                                   |
| P4       | Draggable rows/columns           | Drag-and-drop operations on rows and columns via header cells                                | Done                  | Implemented using the "dragging" modifier `rememberLazyTableRowDraggingState()`, `rememberLazyTableColumnDraggingState()`, `lazyTableDraggable()` modifier; only supported on header cells (pinned row/co            |


> Notes:
>
> - Keyboard navigation with "tab" moves by all selectable items
>   - On the Device Manager table the focus is circular in the same container;
