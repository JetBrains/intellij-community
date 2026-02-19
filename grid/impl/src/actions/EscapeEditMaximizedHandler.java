package com.intellij.database.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.database.DatabaseDataKeys.DATA_GRID_KEY;
import static com.intellij.database.run.ui.CellViewer.CELL_VIEWER_KEY;

/**
 * @author Liudmila Kornilova
 **/
public class EscapeEditMaximizedHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public EscapeEditMaximizedHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return editor.getUserData(CELL_VIEWER_KEY) != null ||
           myOriginalHandler != null && myOriginalHandler.isEnabled(editor, caret, dataContext);
  }

  @Override
  protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (myOriginalHandler != null && myOriginalHandler.isEnabled(editor, caret, dataContext)) {
      myOriginalHandler.execute(editor, caret, dataContext);
      return;
    }
    DataGrid grid = DATA_GRID_KEY.getData(dataContext);
    if (grid == null) return;
    grid.getResultView().getComponent().requestFocus();
  }
}
