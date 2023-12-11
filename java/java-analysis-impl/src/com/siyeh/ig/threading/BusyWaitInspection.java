/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public final class BusyWaitInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("busy.wait.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BusyWaitVisitor();
  }

  private static class BusyWaitVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!MethodCallUtils.isCallToMethod(expression, "java.lang.Thread",
                                          PsiTypes.voidType(), "sleep", PsiTypes.longType()) &&
          !MethodCallUtils.isCallToMethod(expression,
                                          "java.lang.Thread", PsiTypes.voidType(), "sleep",
                                          PsiTypes.longType(), PsiTypes.intType())) {
        return;
      }
      PsiElement context = expression;
      while (true) {
        PsiConditionalLoopStatement loopStatement = PsiTreeUtil.getParentOfType(context, PsiConditionalLoopStatement.class, true,
                                                                                PsiClass.class, PsiLambdaExpression.class);
        if (loopStatement == null) return;
        context = loopStatement;
        PsiStatement body = loopStatement.getBody();
        if (!PsiTreeUtil.isAncestor(body, expression, true)) continue;
        PsiExpression loopCondition = loopStatement.getCondition();
        if (isLocallyBoundLoop(loopCondition)) {
          // Condition depends on locals only: likely they are changed in the loop (or another inspection should fire)
          // so this is not a classic busy wait.
          continue;
        }
        registerMethodCallError(expression);
        return;
      }
    }

    public static boolean isLocallyBoundLoop(PsiExpression loopCondition) {
      loopCondition = PsiUtil.skipParenthesizedExprDown(loopCondition);
      if (loopCondition == null) return false;
      if (ExpressionUtils.computeConstantExpression(loopCondition) == null && ExpressionUtils.isLocallyDefinedExpression(loopCondition)) {
        return true;
      }
      if (loopCondition instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)loopCondition).getOperationTokenType().equals(
        JavaTokenType.ANDAND)) {
        for (PsiExpression operand : ((PsiPolyadicExpression)loopCondition).getOperands()) {
          if (isCounterCondition(operand)) {
            return true;
          }
        }
      }
      return false;
    }

    private static boolean isCounterCondition(PsiExpression expr) {
      PsiBinaryExpression binOp = tryCast(PsiUtil.skipParenthesizedExprDown(expr), PsiBinaryExpression.class);
      if (binOp == null) return false;
      if (!ComparisonUtils.isComparisonOperation(binOp.getOperationTokenType())) return false;
      PsiExpression compared = null;
      if (PsiUtil.isConstantExpression(binOp.getLOperand())) {
        compared = PsiUtil.skipParenthesizedExprDown(binOp.getROperand());
      }
      else if (PsiUtil.isConstantExpression(binOp.getROperand())) {
        compared = PsiUtil.skipParenthesizedExprDown(binOp.getLOperand());
      }
      if (compared == null) return false;
      if (compared instanceof PsiUnaryExpression) {
        PsiReferenceExpression operand =
          tryCast(PsiUtil.skipParenthesizedExprDown(((PsiUnaryExpression)compared).getOperand()), PsiReferenceExpression.class);
        if (operand != null && PsiUtil.isAccessedForWriting(operand) && operand.getQualifierExpression() == null) {
          PsiElement target = operand.resolve();
          if (target instanceof PsiLocalVariable || target instanceof PsiParameter) return true;
        }
      }
      return false;
    }
  }
}