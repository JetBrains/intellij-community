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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
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
    if (!element.getManager().isInProject(element)) return false;

   // if (!(element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.QUEST)) return false;
    final PsiConditionalExpression conditionalExpression = PsiTreeUtil.getParentOfType(element, PsiConditionalExpression.class);
    if (conditionalExpression == null) return false;
    final String conditionText = conditionalExpression.getCondition().getText();
    final PsiExpression thenExpression = conditionalExpression.getThenExpression();
    final PsiExpression elseExpression = conditionalExpression.getElseExpression();

    return isAvailable(conditionText, thenExpression, elseExpression, 0);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

    final PsiConditionalExpression conditionalExpression = PsiTreeUtil.getParentOfType(element, PsiConditionalExpression.class);
    if (conditionalExpression == null) return;
    PsiExpression thenExpression = conditionalExpression.getThenExpression();
    if (thenExpression == null) return;

    thenExpression = (PsiExpression)thenExpression.copy();
    replaceRecursively(project, conditionalExpression, thenExpression, conditionalExpression.getElseExpression());
    CodeStyleManager.getInstance(project).reformat(conditionalExpression.replace(thenExpression));
  }

  private boolean isAvailable(String conditionText, PsiExpression thenExpression, PsiExpression elseExpression, int level) {
    if (!(thenExpression instanceof PsiCallExpression)) return false;
    final PsiMethod thenMethod = ((PsiCallExpression)thenExpression).resolveMethod();
    final PsiExpressionList thenArgsList = ((PsiCallExpression)thenExpression).getArgumentList();
    if (thenArgsList == null) return false;
    final PsiExpression[] thenExpressions = thenArgsList.getExpressions();

    if (!(elseExpression instanceof PsiCallExpression)) return false;
    final PsiMethod elseMethod = ((PsiCallExpression)elseExpression).resolveMethod();
    final PsiExpressionList elseArgsList = ((PsiCallExpression)elseExpression).getArgumentList();
    if (elseArgsList == null) return false;
    final PsiExpression[] elseExpressions = elseArgsList.getExpressions();

    if (thenMethod != elseMethod || thenMethod == null) return false;

    if (thenExpressions.length != elseExpressions.length) return false;

    final Pair<PsiExpression, PsiExpression> qualifiers = getQualifiers(thenExpression, elseExpression);
    if (qualifiers != null) {
      if (!isSameCall(thenExpressions, elseExpressions) && level == 0){
        return false;
      }
      if (level > 0) {
        setText("Push condition '" + conditionText + "' inside " + (thenMethod.isConstructor() ? "constructor" : "method") + " call");
        return true;
      }
      return level > 0 || isAvailable(conditionText, qualifiers.first, qualifiers.second, level + 1);
    }

    PsiExpression tExpr = null;
    for (int i = 0; i < thenExpressions.length; i++) {
      PsiExpression lExpr = thenExpressions[i];
      PsiExpression rExpr = elseExpressions[i];
      if (!PsiEquivalenceUtil.areElementsEquivalent(lExpr, rExpr)) {
        if (tExpr == null) {
          tExpr = lExpr;
        }
        else {
          return false;
        }
      }
    }
    setText("Push condition '" + conditionText + "' inside " + (thenMethod.isConstructor() ? "constructor" : "method") + " call");
    return true;
  }

  private static boolean isSameCall(PsiExpression[] thenExpressions, PsiExpression[] elseExpressions) {
    for (int i = 0; i < thenExpressions.length; i++) {
      final PsiExpression lExpr = thenExpressions[i];
      final PsiExpression rExpr = elseExpressions[i];
      if (!PsiEquivalenceUtil.areElementsEquivalent(lExpr, rExpr)) {
        return false;
      }
    }
    return true;
  }

  private static Pair<PsiExpression, PsiExpression> getQualifiers(PsiExpression thenExpression, PsiExpression elseExpression) {
    PsiExpression thenQualifier = null;
    PsiExpression elseQualifier = null;
    if (thenExpression instanceof PsiMethodCallExpression && elseExpression instanceof PsiMethodCallExpression) {
      thenQualifier = ((PsiMethodCallExpression)thenExpression).getMethodExpression().getQualifierExpression();
      elseQualifier = ((PsiMethodCallExpression)elseExpression).getMethodExpression().getQualifierExpression();
    }
    else if (thenExpression instanceof PsiNewExpression && elseExpression instanceof PsiNewExpression) {
      thenQualifier = ((PsiNewExpression)thenExpression).getQualifier();
      elseQualifier = ((PsiNewExpression)elseExpression).getQualifier();
    }

    if (thenQualifier == null ^ elseQualifier == null ||
        thenQualifier != null && !PsiEquivalenceUtil.areElementsEquivalent(thenQualifier, elseQualifier)) {
      return Pair.create(thenQualifier, elseQualifier);
    }

    return null;
  }

  private static void replaceRecursively(@NotNull Project project,
                                         PsiConditionalExpression conditionalExpression,
                                         PsiExpression thenExpression,
                                         PsiExpression elseExpression) {
    final PsiExpressionList thenArgsList = ((PsiCallExpression)thenExpression).getArgumentList();
    if (thenArgsList == null) return;
    final PsiExpression[] thenExpressions = thenArgsList.getExpressions();

    final PsiExpressionList elseArgsList = ((PsiCallExpression)elseExpression).getArgumentList();
    if (elseArgsList == null) return;
    final PsiExpression[] elseExpressions = elseArgsList.getExpressions();

    final Pair<PsiExpression, PsiExpression> qualifiers = getQualifiers(thenExpression, elseExpression);
    if (qualifiers != null) {
      if (isSameCall(thenExpressions, elseExpressions)) {
        replaceRecursively(project, conditionalExpression, qualifiers.first, qualifiers.second);
      }
      else {
        pushConditional(project, conditionalExpression, thenExpression, elseExpression);
      }
    }
    else {
      for (int i = 0; i < thenExpressions.length; i++) {
        PsiExpression lExpr = thenExpressions[i];
        PsiExpression rExpr = elseExpressions[i];
        if (pushConditional(project, conditionalExpression, lExpr, rExpr)) {
          break;
        }
      }
    }
  }

  private static boolean pushConditional(@NotNull Project project,
                                         PsiConditionalExpression conditionalExpression,
                                         PsiExpression thenExpression,
                                         PsiExpression elseExpression) {
    if (!PsiEquivalenceUtil.areElementsEquivalent(thenExpression, elseExpression)) {
      thenExpression.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(
        conditionalExpression.getCondition().getText() + "?" + thenExpression.getText() + ":" + elseExpression.getText(), thenExpression));
      return true;
    }
    return false;
  }
}
