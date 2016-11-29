/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public abstract class ElementCreator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.ElementCreator");
  private final Project myProject;
  private final String myErrorTitle;

  protected ElementCreator(Project project, String errorTitle) {
    myProject = project;
    myErrorTitle = errorTitle;
  }

  protected abstract PsiElement[] create(String newName) throws Exception;
  protected abstract String getActionName(String newName);

  public PsiElement[] tryCreate(@NotNull final String inputString) {
    if (inputString.length() == 0) {
      Messages.showMessageDialog(myProject, IdeBundle.message("error.name.should.be.specified"), CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return PsiElement.EMPTY_ARRAY;
    }

    final Exception[] exception = new Exception[1];
    final SmartPsiElementPointer[][] myCreatedElements = {null};

    final String commandName = getActionName(inputString);
    new WriteCommandAction(myProject, commandName) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        LocalHistoryAction action = LocalHistoryAction.NULL;
        try {
          action = LocalHistory.getInstance().startAction(commandName);

          PsiElement[] psiElements = create(inputString);
          myCreatedElements[0] = new SmartPsiElementPointer[psiElements.length];
          SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
          for (int i = 0; i < myCreatedElements[0].length; i++) {
            myCreatedElements[0][i] = manager.createSmartPsiElementPointer(psiElements[i]);
          }
        }
        catch (Exception ex) {
          exception[0] = ex;
        }
        finally {
          action.finish();
        }
      }

      @Override
      protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
        return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
      }
    }.execute();

    if (exception[0] != null) {
      LOG.info(exception[0]);
      String errorMessage = CreateElementActionBase.filterMessage(exception[0].getMessage());
      if (errorMessage == null || errorMessage.length() == 0) {
        errorMessage = exception[0].toString();
      }
      Messages.showMessageDialog(myProject, errorMessage, myErrorTitle, Messages.getErrorIcon());
      return PsiElement.EMPTY_ARRAY;
    }

    List<PsiElement> result = new SmartList<>();
    for (final SmartPsiElementPointer pointer : myCreatedElements[0]) {
      ContainerUtil.addIfNotNull(result, pointer.getElement());
    }
    return PsiUtilCore.toPsiElementArray(result);
  }
}
