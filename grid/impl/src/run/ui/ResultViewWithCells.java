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

  @Nullable
  TableCellEditor getCellEditor();

  @Nullable
  Color getCellBackground(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column, boolean selected);

  @NotNull
  Color getCellForeground(boolean selected);

  static @NotNull Component prepareComponent(@NotNull Component component,
                                             @NotNull DataGrid grid,
                                             @NotNull ResultViewWithCells resultView,
                                             @NotNull ViewIndex<GridRow> row,
                                             @NotNull ViewIndex<GridColumn> column,
                                             boolean forDisplay) {
    if (!forDisplay) return component;
    JComponent unwrapped = (JComponent)ExpandedItemRendererComponentWrapper.unwrap(component);

    SelectionModel<GridRow, GridColumn> selectionModel = SelectionModelUtil.get(grid, (ResultView)resultView);

    boolean selected = selectionModel.isSelected(row, column);
    Color background = resultView.getCellBackground(row, column, selected);
    Color foreground = resultView.getCellForeground(selected);

    if (background != null) {
      unwrapped.setBackground(background);
    }
    else if (!isRowBgPaintedByTable(grid.getResultView().isTransposed() ? column : row, grid, selected)) {
      unwrapped.setBackground(((ResultView)resultView).getComponent().getBackground());
    }
    unwrapped.setForeground(foreground);
    unwrapped.setOpaque(!Comparing.equal(unwrapped.getBackground(), ((ResultView)resultView).getComponent().getBackground()));
    if (unwrapped instanceof CellRendererPanel) {
      ((CellRendererPanel)unwrapped).setSelected(!Comparing.equal(background, ((ResultView)resultView).getComponent().getBackground()));
    }
    return component;
  }
}
