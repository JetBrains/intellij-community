package com.intellij.database.run.ui.grid.renderers;

import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.ObjectFormatterUtil;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.grid.editors.GridCellEditorHelper;
import com.intellij.database.settings.DataGridAppearanceSettings;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class DefaultBooleanRendererFactory implements GridCellRendererFactory {
  private final DataGrid myGrid;
  private TextBooleanRenderer myTextRenderer;
  private CheckboxBooleanRenderer myCheckboxRenderer;

  public DefaultBooleanRendererFactory(@NotNull DataGrid grid) {
    myGrid = grid;
  }

  @Override
  public boolean supports(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return isBooleanCell(myGrid, row, column);
  }

  private static boolean isBooleanCell(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    if (column.asInteger() == -1) return false; // DBE-17013
    int type = GridCellEditorHelper.get(grid).guessJdbcTypeForEditing(grid, row, column);
    GridModel<GridRow, GridColumn> model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    GridColumn c = Objects.requireNonNull(model.getColumn(column));
    return ObjectFormatterUtil.isBooleanColumn(c, type);
  }

  @Override
  public @NotNull GridCellRenderer getOrCreateRenderer(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    if (myGrid.getAppearance().getBooleanMode() == DataGridAppearanceSettings.BooleanMode.TEXT) {
      if (myTextRenderer == null) {
        myTextRenderer = new TextBooleanRenderer(myGrid);
        Disposer.register(myGrid, myTextRenderer);
      }
      return myTextRenderer;
    }
    if (myCheckboxRenderer == null) {
      myCheckboxRenderer = new CheckboxBooleanRenderer(myGrid);
      Disposer.register(myGrid, myCheckboxRenderer);
    }
    return myCheckboxRenderer;
  }

  @Override
  public void reinitSettings() {
    if (myTextRenderer != null) {
      myTextRenderer.reinitSettings();
    }
    if (myCheckboxRenderer != null) {
      myCheckboxRenderer.reinitSettings();
    }
  }

  public static class TextBooleanRenderer extends DefaultTextRendererFactory.TextRenderer {
    private static final char BULLET = '\u2022';

    public TextBooleanRenderer(@NotNull DataGrid grid) {
      super(grid, new JBEmptyBorder(0, 1, 0, 3));
    }

    @Override
    protected @NotNull String getValueText(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Object value) {
      String text = super.getValueText(columnIdx, value);
      return isTrue(value)
             ? myGrid.getResultView().isTransposed()
               ? text + " " + BULLET
               : " " + BULLET + " " + text
             : myGrid.getResultView().isTransposed()
               ? text
               : "   " + text;
    }

    @Override
    public int getSuitability(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
      return isBooleanCell(myGrid, row, column) ? SUITABILITY_MIN : SUITABILITY_UNSUITABLE;
    }
  }

  public static class CheckboxBooleanRenderer extends GridCellRenderer {
    final DefaultTextRendererFactory.TextRenderer reservedCellValuesRenderer;
    final JBCheckBox myComponent;

    public CheckboxBooleanRenderer(@NotNull DataGrid grid) {
      super(grid);
      reservedCellValuesRenderer = new DefaultTextRendererFactory.TextRenderer(grid);
      Disposer.register(this, reservedCellValuesRenderer);
      myComponent = new JBCheckBox();
      myComponent.setHorizontalAlignment(SwingConstants.CENTER);
    }

    @Override
    public int getSuitability(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
      return isBooleanCell(myGrid, row, column) ? SUITABILITY_MIN : SUITABILITY_UNSUITABLE;
    }

    @Override
    public @NotNull JComponent getComponent(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column, @Nullable Object value) {
      if (isBoolean(value)) {
        myComponent.setSelected(isTrue(value));
        return myComponent;
      }
      return reservedCellValuesRenderer.getComponent(row, column, value);
    }

    @Override
    public void reinitSettings() {
      reservedCellValuesRenderer.reinitSettings();
    }
  }

  private static boolean isBoolean(@Nullable Object value) {
    return value instanceof Boolean ||
           value instanceof String && (StringUtil.equalsIgnoreCase((String)value, "true") || StringUtil.equalsIgnoreCase((String)value, "false"));
  }

  private static boolean isTrue(@Nullable Object value) {
    return Boolean.TRUE.equals(value) || value instanceof String && StringUtil.equalsIgnoreCase((String)value, "true");
  }
}
