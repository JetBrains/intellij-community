package com.intellij.database.run.ui.grid.renderers;

import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.ObjectFormatterUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

public class DefaultNumericRendererFactory implements GridCellRendererFactory {
  private final DataGrid myGrid;
  private NumericRenderer myRenderer;

  public DefaultNumericRendererFactory(@NotNull DataGrid grid) {
    myGrid = grid;
  }

  @Override
  public boolean supports(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return ObjectFormatterUtil.isNumericCell(myGrid, row, column);
  }

  @Override
  public @NotNull GridCellRenderer getOrCreateRenderer(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    if (myRenderer == null) {
      myRenderer = new NumericRenderer(myGrid);
      Disposer.register(myGrid, myRenderer);
    }
    return myRenderer;
  }

  @Override
  public void reinitSettings() {
    if (myRenderer != null) {
      myRenderer.reinitSettings();
    }
  }

  private static class NumericRenderer extends DefaultTextRendererFactory.TextRenderer {
    NumericRenderer(@NotNull DataGrid grid) {
      super(grid);
    }

    @Override
    protected void configureEditor(@NotNull EditorEx editor) {
      GridUtil.configureNumericEditor(myGrid, editor);
      super.configureEditor(editor);
    }

    @Override
    public int getSuitability(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
      return SUITABILITY_MIN + 1;
    }
  }
}
