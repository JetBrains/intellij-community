/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.*;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class AddMethodQualifierFix implements IntentionAction {
  private static final boolean UNIT_TEST_MODE = ApplicationManager.getApplication().isUnitTestMode();

  private final SmartPsiElementPointer<PsiMethodCallExpression> myMethodCall;
  private List<PsiVariable> myCandidates = null;

  public AddMethodQualifierFix(final PsiMethodCallExpression methodCallExpression) {
    myMethodCall = SmartPointerManager.getInstance(methodCallExpression.getProject()).createSmartPsiElementPointer(methodCallExpression);
  }

  @NotNull
  @Override
  public String getText() {
    String text = QuickFixBundle.message("add.method.qualifier.fix.text", myCandidates.size() > 1 ? "" : myCandidates.get(0).getName());
    if (myCandidates.size() > 1) {
      text += "...";
    }
    return text;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    final PsiMethodCallExpression element = myMethodCall.getElement();
    if (element == null || !element.isValid()) {
      return false;
    }
    return getOrFindCandidates().size() != 0;
  }

  private synchronized List<PsiVariable> getOrFindCandidates() {
    if (myCandidates == null) {
      findCandidates();
    }
    return myCandidates;
  }

  private void findCandidates() {
    myCandidates = new ArrayList<PsiVariable>();
    final PsiMethodCallExpression methodCallElement = myMethodCall.getElement();
    final String methodName = methodCallElement.getMethodExpression().getReferenceName();
    if (methodName == null) {
      return;
    }

    for (final PsiVariable var : CreateFromUsageUtils.guessMatchingVariables(methodCallElement)) {
      if (var.getName() == null) {
        continue;
      }
      final PsiType type = var.getType();
      if (!(type instanceof PsiClassType)) {
        continue;
      }
      final PsiClass resolvedClass = ((PsiClassType)type).resolve();
      if (resolvedClass == null) {
        continue;
      }
      if (resolvedClass.findMethodsByName(methodName, true).length > 0) {
        myCandidates.add(var);
      }
    }
  }

  @TestOnly
  public List<PsiVariable> getCandidates() {
    return myCandidates;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(file)) {
      return;
    }
    if (myCandidates.size() == 1 || UNIT_TEST_MODE) {
      qualify(myCandidates.get(0), editor);
    }
    else {
      chooseAndQualify(editor);
    }
  }

  private void chooseAndQualify(final Editor editor) {
    final BaseListPopupStep<PsiVariable> step =
      new BaseListPopupStep<PsiVariable>(QuickFixBundle.message("add.qualifier"), myCandidates) {
        @Override
        public PopupStep onChosen(final PsiVariable selectedValue, final boolean finalChoice) {
          if (selectedValue != null && finalChoice) {
            WriteCommandAction.runWriteCommandAction(selectedValue.getProject(), new Runnable() {
              @Override
              public void run() {
                qualify(selectedValue, editor);
              }
            });
          }
          return FINAL_CHOICE;
        }

        @NotNull
        @Override
        public String getTextFor(final PsiVariable value) {
          return value.getName();
        }

        @Override
        public Icon getIconFor(final PsiVariable aValue) {
          return aValue.getIcon(0);
        }
      };

    final ListPopupImpl popup = new ListPopupImpl(step);
    popup.showInBestPositionFor(editor);
  }

  private void qualify(final PsiVariable qualifier, final Editor editor) {
    final String qualifierPresentableText = qualifier.getName();
    final PsiMethodCallExpression oldExpression = myMethodCall.getElement();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(qualifier.getProject());
    final PsiExpression expression = elementFactory
      .createExpressionFromText(qualifierPresentableText + "." + oldExpression.getMethodExpression().getReferenceName() + "()", null);
    final PsiElement replacedExpression = oldExpression.replace(expression);
    editor.getCaretModel().moveToOffset(replacedExpression.getTextOffset() + replacedExpression.getTextLength());
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}