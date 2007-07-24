package com.intellij.ide.actions;

import com.intellij.ide.util.GotoLineNumberDialog;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;

public class GotoLineAction extends AnAction {
  public GotoLineAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final Editor editor = e.getData(DataKeys.EDITOR);
    if (Boolean.TRUE.equals(e.getData(DataKeys.IS_MODAL_CONTEXT))) {
      GotoLineNumberDialog dialog = new GotoLineNumberDialog(project, editor);
      dialog.show();
    }
    else {
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
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = event.getData(DataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    Editor editor = event.getData(DataKeys.EDITOR);
    presentation.setEnabled(editor != null);
    presentation.setVisible(editor != null);
  }
}
