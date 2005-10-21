
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ide.IdeBundle;

import javax.swing.*;

public class PrevSplitAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    final CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      project, new Runnable(){
        public void run() {
          final FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(project);
          manager.setCurrentWindow(manager.getPrevWindow(manager.getCurrentWindow()));
        }
      }, IdeBundle.message("command.go.to.prev.split"), null
    );
  }
  
  public void update(final AnActionEvent event){
    final Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    final Presentation presentation = event.getPresentation();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    final FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(project);
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    presentation.setEnabled (toolWindowManager.isEditorComponentActive() && manager.isInSplitter() && manager.getCurrentWindow() != null);
  }
}
