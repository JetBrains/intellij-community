/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class AddMethodQualifierFix implements IntentionAction {
  private static final boolean UNIT_TEST_MODE = ApplicationManager.getApplication().isUnitTestMode();
  private enum SearchMode { MAX_2_CANDIDATES, FULL_SEARCH }

  private final SmartPsiElementPointer<PsiMethodCallExpression> myMethodCall;
  private List<PsiVariable> myCandidates;

  public AddMethodQualifierFix(final PsiMethodCallExpression methodCallExpression) {
    myMethodCall = SmartPointerManager.getInstance(methodCallExpression.getProject()).createSmartPsiElementPointer(methodCallExpression);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiMethodCallExpression element = myMethodCall.getElement();
    if (element == null || myCandidates.isEmpty()) {
      return IntentionPreviewInfo.EMPTY;
    }
    PsiMethodCallExpression copyExpression = PsiTreeUtil.findSameElementInCopy(element, file);
    replaceWithQualifier(myCandidates.get(0), copyExpression);
    return IntentionPreviewInfo.DIFF;
  }

  @NotNull
  @Override
  public String getText() {
    if (myCandidates == null || myCandidates.isEmpty()) {
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        return "";
      }
      throw new IllegalStateException();
    }
    if (myCandidates.size() == 1) {
      return QuickFixBundle.message("add.method.qualifier.fix.text", myCandidates.get(0).getName());
    } else {
      return getFamilyName();
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("add.method.qualifier.fix.family");
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    final PsiMethodCallExpression element = myMethodCall.getElement();
    if (element == null || !element.isValid() || element.getMethodExpression().getQualifierExpression() != null) {
      return false;
    }
    if (myCandidates == null) {
      myCandidates = findCandidates(SearchMode.MAX_2_CANDIDATES);
    }
    return !myCandidates.isEmpty();
  }

  private List<PsiVariable> findCandidates(@NotNull SearchMode mode) {
    List<PsiVariable> candidates = new ArrayList<>();
    final PsiMethodCallExpression methodCallElement = myMethodCall.getElement();
    if (methodCallElement == null) {
      return Collections.emptyList();
    }
    final String methodName = methodCallElement.getMethodExpression().getReferenceName();
    if (methodName == null) {
      return Collections.emptyList();
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
        candidates.add(var);
        if (mode == SearchMode.MAX_2_CANDIDATES && candidates.size() >= 2) {
          return candidates;
        }
      }
    }
    return candidates;
  }

  @TestOnly
  public List<PsiVariable> getCandidates() {
    return findCandidates(SearchMode.FULL_SEARCH);
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return myMethodCall.getElement();
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    List<PsiVariable> candidates = findCandidates(SearchMode.FULL_SEARCH);
    if (candidates.size() == 1 || UNIT_TEST_MODE) {
      qualify(candidates.get(0), editor);
    }
    else {
      chooseAndQualify(project, editor, candidates);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private void chooseAndQualify(Project project, Editor editor, List<? extends PsiVariable> candidates) {
    final BaseListPopupStep<PsiVariable> step =
      new BaseListPopupStep<>(QuickFixBundle.message("add.qualifier"), candidates) {
        @Override
        public PopupStep onChosen(final PsiVariable selectedValue, final boolean finalChoice) {
          if (selectedValue != null && finalChoice) {
            qualify(selectedValue, editor);
          }
          return FINAL_CHOICE;
        }

        @NotNull
        @Override
        public String getTextFor(final PsiVariable value) {
          String name = value.getName();
          return name == null ? "" : name;
        }

        @Override
        public Icon getIconFor(final PsiVariable aValue) {
          return aValue.getIcon(0);
        }
      };

    JBPopup popup = JBPopupFactory.getInstance().createListPopup(project, step, (baseRenderer) -> baseRenderer);
    popup.showInBestPositionFor(editor);
  }

  private void qualify(final PsiVariable qualifier, final Editor editor) {
    WriteCommandAction.runWriteCommandAction(qualifier.getProject(), () -> {
      PsiMethodCallExpression element = myMethodCall.getElement();
      if (element == null) {
        return;
      }
      final PsiElement replacedExpression = replaceWithQualifier(qualifier, element);
      editor.getCaretModel().moveToOffset(replacedExpression.getTextOffset() + replacedExpression.getTextLength());
    });
  }

  private static PsiElement replaceWithQualifier(@NotNull PsiVariable qualifier,
                                                 @NotNull PsiMethodCallExpression oldExpression) {
    final String qualifierPresentableText = qualifier.getName();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(qualifier.getProject());
    final PsiExpression expression = elementFactory
      .createExpressionFromText(qualifierPresentableText + "." + oldExpression.getMethodExpression().getReferenceName() + "()", null);
    return oldExpression.replace(expression);
  }
}