package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.FileEditorManager;

/**
 * @author nik
 * internal action
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class OpenRemoteFileAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    String defaultUrl = "http://localhost:8080/index.html";
    String url = Messages.showInputDialog(project, "URL", "Open Remote File", null, defaultUrl, null);
    if (url != null) {
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file == null) {
        Messages.showErrorDialog(project, "Cannot find file '" + url + "'", "Cannot Open File");
      }
      else {
        FileEditorManager.getInstance(project).openFile(file, true);
      }
    }
  }
}
