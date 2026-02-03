package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.util.ui.CalendarView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class GridCellEditorFactoryImpl implements GridCellEditorFactoryProvider {
  private static final GridCellEditorFactoryImpl INSTANCE = new GridCellEditorFactoryImpl();

  protected final List<? extends GridCellEditorFactory> myDefaultFactories = createFactories();

  protected List<? extends GridCellEditorFactory> createFactories() {
    return Arrays
      .asList(new DefaultNumericEditorFactory(), new DefaultDateEditorFactory(), new DefaultTimestampEditorFactory(CalendarView.Mode.DATE),
              new DefaultTimestampEditorFactory(CalendarView.Mode.TIME), new DefaultTimestampEditorFactory(CalendarView.Mode.DATETIME),
              new DefaultTimeEditorFactory(), new DefaultTextEditorFactory(), new DefaultBlobEditorFactory(),
              new DefaultBooleanEditorFactory());
  }

  public static GridCellEditorFactoryProvider getInstance() {
    return INSTANCE;
  }

  @Override
  public @Nullable GridCellEditorFactory getEditorFactory(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return GridCellEditorFactoryProvider.getEditorFactory(myDefaultFactories, factory -> factory.getSuitability(grid, row, column), GridCellEditorFactory.class);
  }
}
