package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.GridCellRequest;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
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
  public @Nullable GridCellEditorFactory getEditorFactory(@NotNull GridCellRequest<GridRow, GridColumn> request) {
    return GridCellEditorFactoryProvider.getEditorFactory(myDefaultFactories, factory -> factory.getSuitability(request), GridCellEditorFactory.class);
  }
}
