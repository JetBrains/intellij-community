package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ViewIndex
import com.intellij.database.datagrid.request
import com.intellij.database.run.ui.ResultViewWithCells
import com.intellij.database.run.ui.grid.CellAttributes
import com.intellij.database.run.ui.grid.renderers.GridCellRenderer
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap
import java.awt.Color
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * Renders the view's cells: the Swing adapters of the grid's renderers, and the cell colors from the grid's color model, colors scheme and
 * markup. There is one adapter per renderer; it reads the table that Swing passes in, so both tables share it. It holds the grid because
 * the grid's renderers take the grid's cell requests; the tables get only Swing renderers and colors from it. It reads the colors on each
 * call, since the grid replaces its color model without telling the view.
 */
internal class ViewCellRenderers(private val grid: DataGrid) {
  private val adapters = Reference2ObjectOpenHashMap<GridCellRenderer, GridCellRendererAdapter>()

  /** The adapter of the grid's renderer for the cell at [row] and [column]. */
  fun rendererFor(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): TableCellRenderer {
    val renderer = GridCellRenderer.getRenderer(grid.request(row, column))
    return adapters.getOrPut(renderer) { GridCellRendererAdapter(renderer) }
  }

  /** Drops what the grid's renderer for the cell at [row] and [column] cached, as after a change of the column's display type. */
  fun clearRendererCache(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>) {
    GridCellRenderer.getRenderer(grid.request(row, column)).clearCache()
  }

  /**
   * Colors [component], which renders the cell at [row] and [localIndex] of [table], for display. A selected cell takes the table's
   * selection colors; any other cell takes its background from the grid's color model. The two positions stay the table's own, as
   * `prepareRenderer` gets them: only [table] knows its slice and whether it is transposed.
   */
  fun prepare(component: Component, table: GridTable, row: Int, localIndex: Int, selected: Boolean): Component {
    // While transposed, a table row is a grid column, and a table column is a grid row.
    val transposed = table.isTransposed
    val viewRow = ViewIndex.forRow(grid, if (transposed) localIndex else row)
    val viewColumn = ViewIndex.forColumn(grid, if (transposed) row else table.toGridViewColumn(localIndex))
    val background =
      if (selected) table.selectionBackground else cellBackground(table.modelRowAt(row, localIndex), table.modelColumnAt(row, localIndex))
    return ResultViewWithCells.prepareComponent(component, grid, selected, background, table.getCellForeground(selected), table.background,
                                                viewRow, viewColumn)
  }

  /** The background the grid's color model gives the unselected cell at [row] and [column], or null for the view's background. */
  fun cellBackground(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): Color? = grid.colorModel.getCellBackground(row, column)

  /** What the grid's markup paints over the cell at [row] and [column], or null for nothing. */
  fun cellEffect(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): CellAttributes? =
    grid.markupModel.getCellAttributes(row, column, grid.colorsScheme)

  /** The background of every other row while the tables are striped. */
  fun stripeRowBackground(): Color? = grid.stripeRowBackground

  /** The background of the row under the pointer. */
  fun hoveredRowBackground(): Color? = grid.hoveredRowBackground

  private inner class GridCellRendererAdapter(private val delegate: GridCellRenderer) : TableCellRenderer {
    override fun getTableCellRendererComponent(
      table: JTable,
      value: Any?,
      isSelected: Boolean,
      hasFocus: Boolean,
      row: Int,
      column: Int,
    ): Component {
      // Only GridTable.getCellRenderer creates the adapters, so Swing passes a GridTable.
      val gridTable = table as GridTable
      val transposed = gridTable.isTransposed
      val viewRow = ViewIndex.forRow(grid, if (transposed) column else row)
      // The renderer works in the grid's column space, not the table's. In a transposed table its row is the grid column.
      val gridViewColumn = if (transposed) row else gridTable.toGridViewColumn(column)
      val viewColumn = ViewIndex.forColumn(grid, gridViewColumn)
      return delegate.getComponent(viewRow, viewColumn, value)
    }
  }
}
