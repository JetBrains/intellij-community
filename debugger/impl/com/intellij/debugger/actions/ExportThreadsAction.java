/**
 * class ExportThreadsAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.ExportDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.idea.ActionsBundle;
import com.intellij.util.SystemProperties;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ExportThreadsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();

    if(context.getDebuggerSession() != null) {
      final String destinationDirectory;
      final VirtualFile projectFile = project.getProjectFile();
      if (projectFile != null) {
        destinationDirectory = projectFile.getParent().getPresentableUrl();
      }
      else {
        destinationDirectory = "";
      }
      ExportDialog dialog = new ExportDialog(context.getDebugProcess(),  destinationDirectory);
      dialog.show();
      if (dialog.isOK()) {
        try {
          File file = new File(dialog.getFilePath());
          BufferedWriter writer = new BufferedWriter(new FileWriter(file));
          try {
            String text = StringUtil.convertLineSeparators(dialog.getTextToSave(), SystemProperties.getLineSeparator());
            writer.write(text);
          }
          finally {
            writer.close();
          }
        }
        catch (IOException ex) {
          Messages.showMessageDialog(project, ex.getMessage(), ActionsBundle.actionText(DebuggerActions.EXPORT_THREADS), Messages.getErrorIcon());
        }
      }
    }
  }



  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
    presentation.setEnabled(debuggerSession != null && debuggerSession.isPaused());
  }
}