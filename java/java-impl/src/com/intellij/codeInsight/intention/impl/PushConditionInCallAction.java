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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PushConditionInCallAction extends PsiElementBaseIntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return "Push condition inside call";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {

    if (element instanceof PsiCompiledElement) return false;
    if (!GeneratedSourcesFilter.isInProjectAndNotGenerated(element)) return false;

   // if (!(element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.QUEST)) return false;
    final PsiConditionalExpression conditionalExpression = PsiTreeUtil.getParentOfType(element, PsiConditionalExpression.class);
    if (conditionalExpression == null) return false;
    final PsiExpression thenExpression = conditionalExpression.getThenExpression();
    if (!(thenExpression instanceof PsiCallExpression)) return false;
    final PsiMethod thenMethod = ((PsiCallExpression)thenExpression).resolveMethod();
    final PsiExpressionList thenArgsList = ((PsiCallExpression)thenExpression).getArgumentList();
    if (thenArgsList == null) return false;
    final PsiExpression[] thenExpressions = thenArgsList.getExpressions();

    final PsiExpression elseExpression = conditionalExpression.getElseExpression();
    if (!(elseExpression instanceof PsiCallExpression)) return false;
    final PsiMethod elseMethod = ((PsiCallExpression)elseExpression).resolveMethod();
    final PsiExpressionList elseArgsList = ((PsiCallExpression)elseExpression).getArgumentList();
    if (elseArgsList == null) return false;
    final PsiExpression[] elseExpressions = elseArgsList.getExpressions();

    if (thenMethod != elseMethod || thenMethod == null) return false;

    if (thenExpressions.length != elseExpressions.length) return false;

    PsiExpression tExpr = null;
    PsiExpression eExpr = null;
    for (int i = 0; i < thenExpressions.length; i++) {
      PsiExpression lExpr = thenExpressions[i];
      PsiExpression rExpr = elseExpressions[i];
      if (!PsiEquivalenceUtil.areElementsEquivalent(lExpr, rExpr)) {
        if (tExpr == null || eExpr == null) {
          tExpr = lExpr;
          eExpr = rExpr;
        }
        else {
          return false;
        }
      }
    }
    setText("Push condition '" + conditionalExpression.getCondition().getText() + "' inside " +
            (thenMethod.isConstructor() ? "constructor" : "method") + " call");
    return true;
  }


  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

    final PsiConditionalExpression conditionalExpression = PsiTreeUtil.getParentOfType(element, PsiConditionalExpression.class);
    final PsiExpression thenExpression = (PsiExpression)conditionalExpression.getThenExpression().copy();
    final PsiExpressionList thenArgsList = ((PsiCallExpression)thenExpression).getArgumentList();
    final PsiExpression[] thenExpressions = thenArgsList.getExpressions();

    final PsiExpression elseExpression = conditionalExpression.getElseExpression();
    final PsiExpressionList elseArgsList = ((PsiCallExpression)elseExpression).getArgumentList();
    final PsiExpression[] elseExpressions = elseArgsList.getExpressions();


    for (int i = 0; i < thenExpressions.length; i++) {
      PsiExpression lExpr = thenExpressions[i];
      PsiExpression rExpr = elseExpressions[i];
      if (!PsiEquivalenceUtil.areElementsEquivalent(lExpr, rExpr)) {
        lExpr.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(
          conditionalExpression.getCondition().getText() + "?" + lExpr.getText() + ":" + rExpr.getText(), lExpr));
        break;
      }
    }

    CodeStyleManager.getInstance(project).reformat(conditionalExpression.replace(thenExpression));
  }
}
