package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LoadFileAction extends AnAction implements DumbAware {
  public static final DataKey<LoadFileActionHandler> LOAD_FILE_ACTION_HANDLER_KEY = DataKey.create("LoadFileActionHandler");

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(getActionHandler(e.getDataContext()) != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext context = e.getDataContext();
    DataGrid grid = GridUtil.getDataGrid(context);
    LoadFileActionHandler handler = getActionHandler(context);
    if (grid == null || handler == null) return;

    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, true, false, false);
    descriptor.setTitle(DataGridBundle.message("dialog.title.choose.file.to.upload"));
    descriptor.setDescription(DataGridBundle.message("label.chose.file.to.upload.to.selected.database.table.row.column"));
    descriptor.setShowFileSystemRoots(true);

    Project project = e.getProject();
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, grid.getPanel().getComponent());
    VirtualFile chosenFile = ArrayUtil.getFirstElement(chooser.choose(project));
    if (chosenFile != null) {
      handler.fileChosen(chosenFile);
    }
  }

  private static @Nullable LoadFileActionHandler getActionHandler(@NotNull DataContext context) {
    DataGrid grid = GridUtil.getDataGrid(context);
    return grid != null ? (grid.isEditing() ? LOAD_FILE_ACTION_HANDLER_KEY.getData(context) : getViewModeHandler(grid)) : null;
  }

  private static @Nullable LoadFileActionHandler getViewModeHandler(final @NotNull DataGrid grid) {
    SelectionModel<GridRow, GridColumn> selectionModel = grid.getSelectionModel();
    GridModel<GridRow, GridColumn> model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    ModelIndex<GridColumn> column = selectionModel.getSelectedColumn();
    GridColumn selectedColumn = model.getColumn(column);
    ModelIndex<GridRow> row = selectionModel.getSelectedRow();
    if (!grid.isEditable() || selectedColumn == null ||
        selectionModel.getSelectedRowCount() != 1 || selectionModel.getSelectedColumnCount() != 1 ||
        !GridUtil.canInsertBlob(grid, row, column) && !GridUtil.canInsertClob(grid, row, column)) {
      return null;
    }

    final ModelIndexSet<GridRow> selectedRows = selectionModel.getSelectedRows();
    final ModelIndexSet<GridColumn> selectedColumns = selectionModel.getSelectedColumns();
    final boolean fileAsClob = GridUtil.canInsertClob(grid, row, column);

    return new LoadFileActionHandler() {
      @Override
      public void fileChosen(@NotNull VirtualFile file) {
        grid.setCells(selectedRows, selectedColumns, fileAsClob ? GridUtil.clobFromFile(file) : GridUtil.blobFromFile(file));
      }
    };
  }

  public interface LoadFileActionHandler {
    void fileChosen(@NotNull VirtualFile file);
  }
}
