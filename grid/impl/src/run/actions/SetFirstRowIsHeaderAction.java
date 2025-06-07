package com.intellij.database.run.actions;

import com.intellij.database.csv.CsvFormatEditor;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.ViewIndex;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.database.DatabaseDataKeys.DATA_GRID_KEY;
import static com.intellij.database.csv.CsvFormatEditor.CSV_FORMAT_EDITOR_KEY;

/**
 * @author Liudmila Kornilova
 **/
public class SetFirstRowIsHeaderAction extends ToggleAction implements DumbAware {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DATA_GRID_KEY);
    Handler handler = get(e);
    e.getPresentation().setEnabledAndVisible(handler != null && grid != null &&
                                             (grid.getSelectionModel().getSelectedRows().toView(grid).asIterable()
                                                .contains(ViewIndex.forRow(grid, 0)) || grid.getContextColumn().asInteger() != -1));
    super.update(e);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Handler handler = get(e);
    return handler != null && handler.firstRowIsHeader();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    Handler handler = get(e);
    if (handler != null) handler.setFirstRowIsHeader(state);
  }

  public interface Handler {
    Key<Handler> KEY = Key.create("SetFirstRowIsHeaderAction.Handler");
    boolean firstRowIsHeader();
    void setFirstRowIsHeader(boolean selected);
  }
  private static @Nullable Handler get(@NotNull AnActionEvent e) {
    CsvFormatEditor csvEditor = e.getData(CSV_FORMAT_EDITOR_KEY);
    if (csvEditor != null) {
      return new Handler() {
        @Override
        public boolean firstRowIsHeader() {
          return csvEditor.firstRowIsHeader();
        }

        @Override
        public void setFirstRowIsHeader(boolean selected) {
          csvEditor.setFirstRowIsHeader(selected);
        }
      };
    }
    DataGrid grid = e.getData(DATA_GRID_KEY);
    return grid == null ? null : grid.getUserData(Handler.KEY);
  }
}
