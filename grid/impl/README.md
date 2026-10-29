# Grid implementation

`intellij.grid.impl` provides the shared Swing grid, result views, editor integration, and grid actions.
The [core-impl module](../core-impl) provides the models, hook interfaces, and indices; the [grid overview](../README.md) explains the module names.

## Main classes

| Interface or class                                               | Role                                                                                                |
|------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| [DataGrid](src/datagrid/DataGrid.java)                           | Extends `CoreGrid` with UI operations, presentation settings, and grid events.                      |
| [TableResultPanel](src/run/ui/TableResultPanel.java)             | Implements `DataGrid`. Coordinates the hook, active view, column state, and editing.                |
| [GridDataHookUp](../core-impl/src/datagrid/GridDataHookUp.java)  | Supplies models, loading, paging, sorting, filtering, and an optional mutator.                      |
| [GridDataHookUpManager](src/datagrid/GridDataHookUpManager.java) | Registers hooks and shares them through handles tied to a parent disposable.                        |
| [GridModel](../core-impl/src/datagrid/GridModel.java)            | Exposes rows, columns, values, and model events.                                                    |
| [GridMutator](../core-impl/src/datagrid/GridMutator.java)        | Changes data. Additional interfaces support row, column, and database operations.                   |
| [ResultView](src/datagrid/ResultView.java)                       | Connects a presentation to the grid through model events, selection, editing, and index conversion. |
| [ResultViewFactory](src/run/ui/ResultViewFactory.java)           | Creates and wraps the table, text, or tree-table view for the selected `GridPresentationMode`.      |
| [GridTableModel](src/run/ui/table/GridTableModel.java)           | Adapts grid data and events to Swing. Regular and transposed models define the axes.                |

[DataGridListener](src/datagrid/DataGridListener.java) reports content, selection, and presentation changes to other UI components.

## Data and indices

[DataAccessType](../core-impl/src/run/ui/DataAccessType.java) picks the model: `DATABASE_DATA` without pending edits, or `DATA_WITH_MUTATIONS` with them.
`TableResultPanel` listens to the `DATA_WITH_MUTATIONS` model and forwards its events to the active view.
[ModelIndex](../core-impl/src/datagrid/ModelIndex.java) is a position in the data model, and [ViewIndex](../core-impl/src/datagrid/ViewIndex.java) a position in the view.
After the source renumbers columns, an old `ModelIndex` can point to another column; find it again with `GridUtil.findColumn(grid, name)`.
[SelectionModel](../core-impl/src/datagrid/SelectionModel.java) exposes the selection in model coordinates.

## Table view

[TableResultView](src/run/ui/table/TableResultView.java) owns the table view and is its only `ResultView`.
It shows the scrollable columns in the main [GridTable](src/run/ui/table/GridTable.java), and pinned columns in a [FrozenStripTable](src/run/ui/table/FrozenStripTable.kt).
The view owns everything the two tables share:
- the data model, the row selection, and one sorter, which only [TableRowOrder](src/run/ui/table/TableRowOrder.kt) notifies;
- the per-row heights in [RowHeightModel](src/run/ui/table/RowHeightModel.kt);
- the columns in [GridColumns](src/run/ui/table/GridColumns.kt): the visible columns in grid order with their selection, and a cache with every column;
- the cell image cache and the value of an open multi-cell edit.

Each table shows a [GridColumnSlice](src/run/ui/table/GridColumnSlice.kt) of that column model: the main table the unpinned columns, the pinned table the pinned ones.
A slice copies no column, and a column asks the view's columns whether it is pinned.
A table reaches the view only through [GridTableHost](src/run/ui/table/GridTableHost.kt), and code outside the package works with `TableResultView`.
[FrozenColumnsController](src/run/ui/table/FrozenColumnsController.kt) handles what crosses the divider, and [FrozenStripLayout](src/run/ui/table/FrozenStripLayout.kt) places the pinned table with the view's row numbers beside it.

## Columns and pins

[TableResultPanel](src/run/ui/TableResultPanel.java) stores the column order, hidden columns included.
[GridColumnPinModel](src/run/ui/table/GridColumnPinModel.kt) holds the pins in the panel, so they survive a presentation switch and a reload.
[DisplayedColumnOrder](src/run/ui/table/DisplayedColumnOrder.kt) puts pinned columns first for keyboard navigation.
A transposed grid has no pinned columns.

## Embedding and editing

Create a grid with [GridUtil.createDataGrid](src/datagrid/GridUtil.java); its callback installs [GridHelper](src/datagrid/GridHelper.kt) and the cell factories.
An edited value goes through `DataGrid.setValueAt` to the hook's mutator.
With deferred updates, `DATA_WITH_MUTATIONS` shows the pending value until `submit` applies it or `revert` drops it.

## Threading and lifetime

Hooks change their models on the EDT, because [GridMutationModel](../core-impl/src/run/ui/grid/GridMutationModel.java) delivers events synchronously.
Register the grid under the host's disposable, and keep the hook reference until the grid is disposed.
`GridDataHookUpManager.acquire(handle, parent)` ties a shared hook to the parent's lifetime and disposes it with the last reference.

## Tests

Grid tests live in `dbe/database/connectivity/tests/src/grid` and `.../run/ui/table`.
CI runs them only from `DatabaseTestSuite.GridTestSuite`, so register each new class there.
Run them with `./tests.cmd --module intellij.database.connectivity.tests --test '<class>'`.
