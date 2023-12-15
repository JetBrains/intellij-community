/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.testFrameworks;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AssertWithoutMessageInspection extends BaseInspection {

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertionsWithoutMessagesVisitor();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assert.without.message.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  private static class AssertionsWithoutMessagesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      AssertHint assertHint = AssertHint.create(expression, methodName -> AbstractAssertHint.ASSERT_METHOD_2_PARAMETER_COUNT.get(methodName));
      if (assertHint == null) {
        return;
      }
      PsiExpression message = assertHint.getMessage();
      if (message == null) {
        registerMethodCallError(expression, assertHint.isMessageOnFirstPosition());
      }
    }
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    return new AssertWithoutMessageFix((boolean)infos[0]);
  }

  private static final class AssertWithoutMessageFix extends PsiUpdateModCommandQuickFix {
    private final boolean myMessageIsOnFirstPosition;

    private AssertWithoutMessageFix(boolean messageIsOnFirstPosition) {myMessageIsOnFirstPosition = messageIsOnFirstPosition;}

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression methodCallExpr = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (methodCallExpr == null) return;

      PsiExpression newMessageExpr = JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText("\"\"", methodCallExpr);
      PsiExpressionList methodArgs = methodCallExpr.getArgumentList();
      PsiElement createdMessageExpr;
      if (myMessageIsOnFirstPosition) {
        PsiExpression[] methodArgExprs = methodArgs.getExpressions();
        PsiExpression firstMethodArgExpr = methodArgExprs.length > 0 ? methodArgExprs[0] : null;
        createdMessageExpr = methodArgs.addBefore(newMessageExpr, firstMethodArgExpr);
      }
      else {
        createdMessageExpr = methodArgs.add(newMessageExpr);
      }

      updater.moveCaretTo(createdMessageExpr.getTextRange().getStartOffset() + 1);
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("assert.without.message.quick.fix.family.name");
    }
  }
}