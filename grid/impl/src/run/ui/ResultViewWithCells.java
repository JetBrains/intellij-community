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

import static com.intellij.database.datagrid.color.SelectionColorLayer.isRowBgPaintedByTable;

public interface ResultViewWithCells {
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

  /** {@code resultView} must also be a {@link ResultView}; for one table of a view, use the overload below. */
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
                            resultView.getCellForeground(selected), view.getComponent(), row, column);
  }

  /**
   * Prepares a displayed cell that {@code cellComponent} shows, such as one table of a view, with colors already resolved.
   * A null {@code background} gives the cell the background of {@code cellComponent}, unless the table paints the row background.
   */
  static @NotNull Component prepareComponent(@NotNull Component component,
                                             @NotNull DataGrid grid,
                                             boolean selected,
                                             @Nullable Color background,
                                             @NotNull Color foreground,
                                             @NotNull JComponent cellComponent,
                                             @NotNull ViewIndex<GridRow> row,
                                             @NotNull ViewIndex<GridColumn> column) {
    JComponent unwrapped = (JComponent)ExpandedItemRendererComponentWrapper.unwrap(component);

    if (background != null) {
      unwrapped.setBackground(background);
    }
    else if (!isRowBgPaintedByTable(grid.getResultView().isTransposed() ? column : row, grid, selected)) {
      unwrapped.setBackground(cellComponent.getBackground());
    }
    unwrapped.setForeground(foreground);
    unwrapped.setOpaque(!Comparing.equal(unwrapped.getBackground(), cellComponent.getBackground()));
    if (unwrapped instanceof CellRendererPanel) {
      ((CellRendererPanel)unwrapped).setSelected(!Comparing.equal(background, cellComponent.getBackground()));
    }
    return component;
  }
}
