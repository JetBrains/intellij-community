/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 16, 2002
 * Time: 6:16:33 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.template.impl.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;

public class PreviousVariableAction extends EditorAction {
  public PreviousVariableAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
      CommandProcessor.getInstance().setCurrentCommandName(CodeInsightBundle.message("template.previous.variable.command"));
      templateState.previousTab();
    }
  }

  public void update(Editor editor, Presentation presentation, DataContext dataContext) {
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    presentation.setEnabled(templateState != null && !templateState.isFinished());
  }
}
