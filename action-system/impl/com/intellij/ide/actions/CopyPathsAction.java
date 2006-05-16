
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ide.IdeBundle;

import java.awt.datatransfer.StringSelection;

public class CopyPathsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final VirtualFile[] vfa = getFiles(e);
    if (vfa == null || vfa.length <= 0) {
      return;
    }
    CopyPasteManager.getInstance().setContents(new StringSelection(getPaths(vfa)));
  }

  private static String getPaths(VirtualFile[] vfa) {
    final StringBuffer buf = new StringBuffer(vfa.length * 64);
    for (int idx = 0; idx < vfa.length; idx++) {
      if (idx > 0) {
        buf.append("\n");
      }
      buf.append(vfa[idx].getPresentableUrl());
    }
    return buf.toString();
  }

  public void update(AnActionEvent event){
    final VirtualFile[] files = getFiles(event);
    final Presentation presentation = event.getPresentation();
    presentation.setEnabled(files != null && files.length > 0);
    presentation.setText((files != null && files.length == 1)
                         ? IdeBundle.message("action.copy.path")
                         : IdeBundle.message("action.copy.paths"));
  }

  private static VirtualFile[] getFiles(AnActionEvent e) {
    return (VirtualFile[])e.getDataContext().getData(DataConstants.VIRTUAL_FILE_ARRAY);
  }

}
