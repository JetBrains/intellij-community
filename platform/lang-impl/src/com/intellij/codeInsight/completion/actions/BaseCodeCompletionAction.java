// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;

public abstract class BaseCodeCompletionAction extends DumbAwareAction implements HintManagerImpl.ActionToIgnore {

  protected BaseCodeCompletionAction() {
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  protected void invokeCompletion(AnActionEvent e, CompletionType type, int time) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    assert editor != null;
    Project project = editor.getProject();
    assert project != null;
    InputEvent inputEvent = e.getInputEvent();
    createHandler(type, true, false, true).invokeCompletion(project, editor, time, inputEvent != null && inputEvent.getModifiers() != 0);
  }

  public @NotNull CodeCompletionHandlerBase createHandler(@NotNull CompletionType completionType, boolean invokedExplicitly, boolean autopopup, boolean synchronous) {

    return new CodeCompletionHandlerBase(completionType, invokedExplicitly, autopopup, synchronous);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    e.getPresentation().setEnabled(false);

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) return;

    Project project = editor.getProject();
    PsiFile psiFile = project == null ? null : PsiUtilBase.getPsiFileInEditor(editor, project);
    if (psiFile == null) return;

    e.getPresentation().setEnabled(true);
  }
}
