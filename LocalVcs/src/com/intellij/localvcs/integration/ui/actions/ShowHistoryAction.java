package com.intellij.localvcs.integration.ui.actions;

import com.intellij.localvcs.integration.FileFilter;
import com.intellij.localvcs.integration.ui.views.DirectoryHistoryDialog;
import com.intellij.localvcs.integration.ui.views.FileHistoryDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;

public class ShowHistoryAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    VirtualFile f = getFile(e);
    Project p = getProject(e);

    DialogWrapper d = f.isDirectory() ? new DirectoryHistoryDialog(f, p) : new FileHistoryDialog(f, p);
    d.show();
  }

  @Override
  public void update(AnActionEvent e) {
    ProjectRootManager rm = ProjectRootManager.getInstance(getProject(e));
    FileTypeManager tm = FileTypeManager.getInstance();

    FileFilter ff = new FileFilter(rm.getFileIndex(), tm);
    updateEnablingStatus(e, ff);
  }

  public void updateEnablingStatus(AnActionEvent e, FileFilter ff) {
    boolean result = isEnabled(e, ff);
    e.getPresentation().setEnabled(result);
  }

  private boolean isEnabled(AnActionEvent e, FileFilter ff) {
    VirtualFile f = getFile(e);
    if (f == null) return false;
    return ff.isAllowedAndUnderContentRoot(f);
  }

  private VirtualFile getFile(AnActionEvent e) {
    return e.getData(DataKeys.VIRTUAL_FILE);
  }

  private Project getProject(AnActionEvent e) {
    return e.getData(DataKeys.PROJECT);
  }
}
