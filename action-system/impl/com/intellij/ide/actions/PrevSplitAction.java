
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;

public class PrevSplitAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = DataKeys.PROJECT.getData(e.getDataContext());
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
    final Project project = DataKeys.PROJECT.getData(event.getDataContext());
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
