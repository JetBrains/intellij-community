package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.CodeInsightBundle;

public class EscapeHandler extends EditorActionHandler {
  private EditorActionHandler myOriginalHandler;

  public EscapeHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext) {
    if (!editor.getSelectionModel().hasSelection()) { //remove selection has higher precedence over finishing template editing
      final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
      if (templateState != null && !templateState.isFinished()) {
        CommandProcessor.getInstance().setCurrentCommandName(CodeInsightBundle.message("finish.template.command"));
        templateState.gotoEnd();
        return;
      }
    }

    myOriginalHandler.execute(editor, dataContext);
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    if (templateState != null && !templateState.isFinished()) {
      return true;
    } else {
      return myOriginalHandler.isEnabled(editor, dataContext);
    }
  }
}
