// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.WriteActionAware;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ElementCreator implements WriteActionAware {
  private static final Logger LOG = Logger.getInstance(ElementCreator.class);
  private final Project myProject;
  private final @NlsContexts.DialogTitle String myErrorTitle;

  protected ElementCreator(Project project, @NotNull @NlsContexts.DialogTitle String errorTitle) {
    myProject = project;
    myErrorTitle = errorTitle;
  }

  protected abstract PsiElement @NotNull [] create(@NotNull String newName) throws Exception;
  @NlsContexts.Command
  @NotNull
  protected abstract String getActionName(@NotNull String newName);

  public @NotNull PsiElement @NotNull [] tryCreate(@NotNull final String inputString) {
    if (inputString.isEmpty()) {
      return PsiElement.EMPTY_ARRAY;
    }

    Ref<List<SmartPsiElementPointer<?>>> createdElements = Ref.create();
    Exception exception = executeCommand(getActionName(inputString), () -> {
      PsiElement[] psiElements = create(inputString);
      SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
      createdElements.set(ContainerUtil.map(psiElements, manager::createSmartPsiElementPointer));
    });
    if (exception != null) {
      handleException(exception);
      return PsiElement.EMPTY_ARRAY;
    }

    return ContainerUtil.mapNotNull(createdElements.get(), SmartPsiElementPointer::getElement).toArray(PsiElement.EMPTY_ARRAY);
  }

  @Nullable
  private Exception executeCommand(@NotNull @NlsContexts.Command String commandName, @NotNull ThrowableRunnable<? extends Exception> invokeCreate) {
    final Exception[] exception = new Exception[1];
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      LocalHistoryAction action = LocalHistory.getInstance().startAction(commandName);
      try {
        if (startInWriteAction()) {
          WriteAction.run(invokeCreate);
        }
        else {
          invokeCreate.run();
        }
      }
      catch (Exception ex) {
        exception[0] = ex;
      }
      finally {
        action.finish();
      }
    }, commandName, null, UndoConfirmationPolicy.REQUEST_CONFIRMATION);
    return exception[0];
  }

  private void handleException(Exception t) {
    LOG.info(t);
    String errorMessage = getErrorMessage(t);
    Messages.showMessageDialog(myProject, errorMessage, myErrorTitle, Messages.getErrorIcon());
  }

  public static @NlsContexts.DialogMessage String getErrorMessage(Throwable t) {
    String errorMessage = CreateElementActionBase.filterMessage(t.getMessage());
    if (StringUtil.isEmpty(errorMessage)) {
      errorMessage = t.toString();
    }
    return errorMessage;
  }
}
