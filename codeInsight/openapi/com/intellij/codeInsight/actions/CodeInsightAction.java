/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

/**
 * @author Dmitry Avdeev
 */
public abstract class CodeInsightAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    Editor editor = getEditor(dataContext, project);
    actionPerformedImpl(project, editor);
  }

  protected Editor getEditor(final DataContext dataContext, final Project project) {
    return (Editor)dataContext.getData(DataConstants.EDITOR);
  }

  public void actionPerformedImpl(final Project project, final Editor editor) {
    if (editor == null) return;
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) return;
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        final CodeInsightActionHandler handler = getHandler();
        final Runnable action = new Runnable() {
          public void run() {
            handler.invoke(project, editor, psiFile);
          }
        };
        if (handler.startInWriteAction()) {
          ApplicationManager.getApplication().runWriteAction(action);
        }
        else {
          action.run();
        }
      }
    }, getCommandName(), null);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();

    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Editor editor = getEditor(dataContext, project);
    if (editor == null) {
      presentation.setEnabled(false);
      return;
    }

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null || !isValidForFile(project, editor, file)) {
      presentation.setEnabled(false);
    }
    else {
      presentation.setEnabled(isEnabledForFile(project, editor, file));
    }
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return true;
  }

  protected boolean isEnabledForFile(Project project, Editor editor, final PsiFile file) {
    return true;
  }

  protected abstract CodeInsightActionHandler getHandler();

  protected String getCommandName() {
    String text = getTemplatePresentation().getText();
    return text != null ? text : "";
  }
}
