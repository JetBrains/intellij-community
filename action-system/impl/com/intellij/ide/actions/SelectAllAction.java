package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.TextComponentEditorAction;

public class SelectAllAction extends AnAction {
  public SelectAllAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Editor editor = TextComponentEditorAction.getEditorFromContext(dataContext);
    if (editor == null) return;
    CommandProcessor processor = CommandProcessor.getInstance();
    processor.executeCommand(DataKeys.PROJECT.getData(dataContext), new Runnable() {
      public void run() {
        editor.getSelectionModel().setSelection(0, editor.getDocument().getTextLength());
      }
    }, IdeBundle.message("command.select.all"), null);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Editor editor = TextComponentEditorAction.getEditorFromContext(event.getDataContext());
    presentation.setEnabled(editor != null);
  }
}
