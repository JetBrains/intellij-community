package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ide.IdeBundle;

public class SelectAllAction extends AnAction {
  public SelectAllAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor == null) return;
    CommandProcessor processor = CommandProcessor.getInstance();
    processor.executeCommand(
        (Project)dataContext.getData(DataConstants.PROJECT), new Runnable(){
        public void run() {
          editor.getSelectionModel().setSelection(0, editor.getDocument().getTextLength());
        }
      },
      IdeBundle.message("command.select.all"),
      null
    );
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Editor editor = (Editor)event.getDataContext().getData(DataConstants.EDITOR);
    presentation.setEnabled(editor != null);
  }
}
