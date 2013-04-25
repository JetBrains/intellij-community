/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Danila Ponomarenko
 */
public class ReplaceWithTernaryOperatorFix implements LocalQuickFix {
  private final String myText;

  @Override
  @NotNull
  public String getName() {
    return InspectionsBundle.message("inspection.replace.ternary.quickfix", myText);
  }

  public ReplaceWithTernaryOperatorFix(@NotNull PsiExpression expressionToAssert) {
    myText = expressionToAssert.getText();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionsBundle.message("inspection.surround.if.family");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    while (true) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiReferenceExpression || parent instanceof PsiMethodCallExpression) {
        element = parent;
      } else {
        break;
      }
    }
    if (!(element instanceof PsiExpression)) {
      return;
    }
    final PsiExpression expression = (PsiExpression)element;

    final PsiFile file = expression.getContainingFile();
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    final PsiConditionalExpression conditionalExpression = replaceWthConditionalExpression(project, myText + "!=null", expression, suggestDefaultValue(expression));

    final PsiExpression elseExpression = conditionalExpression.getElseExpression();
    if (elseExpression != null) {
      selectInEditor(elseExpression);
    }
  }

  private static void selectInEditor(@NotNull PsiElement element) {
    final Editor editor = PsiUtilBase.findEditor(element);
    if (editor == null) return;

    final TextRange expressionRange = element.getTextRange();
    editor.getCaretModel().moveToOffset(expressionRange.getStartOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().setSelection(expressionRange.getStartOffset(), expressionRange.getEndOffset());
  }

  @NotNull
  private static PsiConditionalExpression replaceWthConditionalExpression(@NotNull Project project,
                                                                          @NotNull String condition,
                                                                          @NotNull PsiExpression expression,
                                                                          @NotNull String defaultValue) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

    final PsiElement parent = expression.getParent();
    final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)factory.createExpressionFromText(
      condition + " ? " + expression.getText() + " : " + defaultValue,
      parent
    );

    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    return (PsiConditionalExpression)expression.replace( codeStyleManager.reformat(conditionalExpression));
  }

  public static boolean isAvailable(@NotNull PsiExpression qualifier, @NotNull PsiExpression expression) {
    if (!qualifier.isValid() || qualifier.getText() == null) {
      return false;
    }

    return !(expression.getParent() instanceof PsiExpressionStatement);
  }

  private static String suggestDefaultValue(@NotNull PsiExpression expression) {
    PsiType type = expression.getType();
    return PsiTypesUtil.getDefaultValueOfType(type);
  }
}
