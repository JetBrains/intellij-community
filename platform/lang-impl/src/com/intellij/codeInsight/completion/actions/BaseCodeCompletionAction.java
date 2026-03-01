// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.NewRdCompletionSupport;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.ide.productMode.IdeProductMode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;

@ApiStatus.Internal
public abstract class BaseCodeCompletionAction extends DumbAwareAction implements HintManagerImpl.ActionToIgnore,
                                                                                  ActionRemoteBehaviorSpecification {

  protected BaseCodeCompletionAction() {
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  protected void invokeCompletion(AnActionEvent e, CompletionType type, int time) {
    if (!isAvailableInCurrentMode()) {
      return;
    }


    Editor editor = e.getData(CommonDataKeys.EDITOR);
    assert editor != null;
    Project project = editor.getProject();
    assert project != null;
    InputEvent inputEvent = e.getInputEvent();
    CodeCompletionHandlerBase handler = createHandler(type, true, false, true);
    handler.invokeCompletion(project, editor, time, inputEvent != null && inputEvent.getModifiers() != 0);
  }

  public @NotNull CodeCompletionHandlerBase createHandler(@NotNull CompletionType completionType,
                                                          boolean invokedExplicitly,
                                                          boolean autopopup,
                                                          boolean synchronous) {
    return new CodeCompletionHandlerBase(completionType, invokedExplicitly, autopopup, synchronous);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!isAvailableInCurrentMode()) {
      e.getPresentation().setEnabled(false);
      return;
    }

    DataContext dataContext = e.getDataContext();
    e.getPresentation().setEnabled(false);

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) return;

    Project project = editor.getProject();
    PsiFile psiFile = project == null ? null : PsiUtilBase.getPsiFileInEditor(editor, project);
    if (psiFile == null) return;

    e.getPresentation().setEnabled(true);
  }

  @Override
  public @Nullable ActionRemoteBehavior getBehavior() {
    return ActionRemoteBehavior.FrontendOtherwiseBackend;
  }

  /**
   * This function is necessary for backward compatibility for completion support in remote-dev.
   * It allows dynamic switching between frontend and backend completion based on the current value of `NewRdCompletionSupport#isFrontendRdCompletionOn()`
   */
  public static boolean isAvailableInCurrentMode() {
    if (IdeProductMode.isMonolith()) {
      return true;
    }

    if (IdeProductMode.isFrontend()) {
      return NewRdCompletionSupport.isFrontendRdCompletionOn();
    }

    if (IdeProductMode.isBackend()) {
      // always true:
      // - if the frontend is able to run completion, then the backend action is not called (because of ActionRemoteBehavior.FrontendOtherwiseBackend),
      //      so it does not matter what this method returns on backend and true is okay.
      // - if the frontend is not able to run completion, then backend action is called
      //      so this method should return true on backend.

      return true;
    }

    throw new IllegalStateException("Unknown product mode: " + IdeProductMode.getInstance());
  }

}
