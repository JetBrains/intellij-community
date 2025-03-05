package com.intellij.database.run.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ViewStructureAction;
import com.intellij.ide.util.FileStructurePopup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class ColumnsListAction extends ColumnHeaderActionBase {
  @Override
  protected void update(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
  }

  @Override
  protected void actionPerformed(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    Project project = e.getProject();
    FileEditor fileEditor = PlatformCoreDataKeys.FILE_EDITOR.getData(DataManager.getInstance().getDataContext(grid.getPanel().getComponent()));
    FileStructurePopup popup = project == null || fileEditor == null ? null : ViewStructureAction.createPopup(project, fileEditor);
    if (popup == null) return;
    VirtualFile file = GridUtil.getVirtualFile(grid);
    popup.setTitle(file != null ? file.getName() : grid.getDisplayName());
    popup.show();
  }
}