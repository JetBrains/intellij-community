package com.intellij.ide.actions;

import com.intellij.ide.util.GotoLineNumberDialog;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;

public class GotoLineAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    CommandProcessor processor = CommandProcessor.getInstance();
    processor.executeCommand(
        project, new Runnable(){
        public void run() {
          GotoLineNumberDialog dialog = new GotoLineNumberDialog(project, editor);
          dialog.show();
          IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
        }
      },
      IdeBundle.message("command.go.to.line"),
      null
    );
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    presentation.setEnabled(editor != null);
    presentation.setVisible(editor != null);
  }
}
