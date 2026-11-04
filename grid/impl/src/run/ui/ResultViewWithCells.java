package com.intellij.database.run.ui;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ResultView;
import com.intellij.database.datagrid.SelectionModel;
import com.intellij.database.datagrid.SelectionModelUtil;
import com.intellij.database.datagrid.ViewIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.CellRendererPanel;
import com.intellij.ui.ExpandedItemRendererComponentWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.table.TableCellEditor;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import static com.intellij.database.datagrid.color.SelectionColorLayer.isRowBgPaintedByTable;

public interface ResultViewWithCells {
  /** A one-step move of the lead cell: down or up a row, or to the next or previous cell as Tab and Shift+Tab do. */
  enum LeadMove { NEXT_ROW, PREVIOUS_ROW, NEXT_CELL, PREVIOUS_CELL }

  /**
   * Moves the lead cell one step, as the key bound to {@code move} does in the view's cells. A cell editor that consumed the key calls
   * this.
   */
  void moveLead(@NotNull LeadMove move);

  boolean isCellEditingAllowed();

  void editSelectedCell();

  void editSelectedCellWithValue(Object value);

  @Nullable
  TableCellEditor getCellEditor();

  /** The component of the active cell editor, or null when no cell is being edited. */
  default @Nullable Component getEditorComponent() {
    return null;
  }

  @Nullable
  Color getCellBackground(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column, boolean selected);

  @NotNull
  Color getCellForeground(boolean selected);

  /** The font of the view's cells; the text cell renderer takes its size. */
  @NotNull
  Font getCellFont();

  /** {@code resultView} must also be a {@link ResultView}; for colors already resolved, use the overload below. */
  static @NotNull Component prepareComponent(@NotNull Component component,
                                             @NotNull DataGrid grid,
                                             @NotNull ResultViewWithCells resultView,
                                             @NotNull ViewIndex<GridRow> row,
                                             @NotNull ViewIndex<GridColumn> column,
                                             boolean forDisplay) {
    if (!forDisplay) return component;
    ResultView view = (ResultView)resultView;
    SelectionModel<GridRow, GridColumn> selectionModel = SelectionModelUtil.get(grid, view);
    boolean selected = selectionModel.isSelected(row, column);
    return prepareComponent(component, grid, selected, resultView.getCellBackground(row, column, selected),
                            resultView.getCellForeground(selected), view.getBackground(), row, column);
  }

  /**
   * Prepares a displayed cell with colors already resolved.
   * A null {@code background} gives the cell {@code cellsBackground}, the background of the view or of the table that renders it. The
   * exception is a row whose background the table paints.
   */
  static @NotNull Component prepareComponent(@NotNull Component component,
                                             @NotNull DataGrid grid,
                                             boolean selected,
                                             @Nullable Color background,
                                             @NotNull Color foreground,
                                             @NotNull Color cellsBackground,
                                             @NotNull ViewIndex<GridRow> row,
                                             @NotNull ViewIndex<GridColumn> column) {
    // A wrapper without a delegate has nothing to prepare.
    if (!(ExpandedItemRendererComponentWrapper.unwrap(component) instanceof JComponent unwrapped)) return component;

    if (background != null) {
      unwrapped.setBackground(background);
    }
    else if (!isRowBgPaintedByTable(grid.getResultView().isTransposed() ? column : row, grid, selected)) {
      unwrapped.setBackground(cellsBackground);
    }
    unwrapped.setForeground(foreground);
    unwrapped.setOpaque(!Comparing.equal(unwrapped.getBackground(), cellsBackground));
    if (unwrapped instanceof CellRendererPanel panel) {
      panel.setSelected(!Comparing.equal(background, cellsBackground));
    }
    return component;
  }
}
