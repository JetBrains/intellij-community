/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceAssignmentWithComparisonFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public ReplaceAssignmentWithComparisonFix(PsiAssignmentExpression expr) {super(expr);}

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiBinaryExpression
      comparisonExpr = (PsiBinaryExpression)JavaPsiFacade.getElementFactory(project).createExpressionFromText("a==b", startElement);
    PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)startElement;
    comparisonExpr.getLOperand().replace(assignmentExpression.getLExpression());
    PsiExpression rOperand = comparisonExpr.getROperand();
    assert rOperand != null;
    PsiExpression rExpression = assignmentExpression.getRExpression();
    assert  rExpression != null;
    rOperand.replace(rExpression);
    CodeStyleManager.getInstance(project).reformat(assignmentExpression.replace(comparisonExpr));
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("assignment.used.as.condition.replace.quickfix");
  }
}
