package com.intellij.localvcs.integration.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;

public class ShowHistoryAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    VirtualFile f = e.getData(DataKeys.VIRTUAL_FILE);
    Project p = e.getData(DataKeys.PROJECT);

    DialogWrapper d = f.isDirectory() ? new DirectoryHistoryDialog(f, p) : new FileHistoryDialog(f, p);
    d.show();
  }
}
