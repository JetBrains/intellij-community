/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;

public final class ThrowFromFinallyBlockInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean warnOnAllExceptions = false;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("warnOnAllExceptions", InspectionGadgetsBundle.message("throw,from.finally.block.everywhere.option")));
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    if (infos.length == 0) {
      return InspectionGadgetsBundle.message("throw.from.finally.block.problem.descriptor");
    }
    else {
      final PsiClassType type = (PsiClassType)infos[0];
      return InspectionGadgetsBundle.message("possible.throw.from.finally.block.problem.descriptor", type.getPresentableText());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowFromFinallyBlockVisitor();
  }

  private class ThrowFromFinallyBlockVisitor extends BaseInspectionVisitor {

    @Override
    public void visitCallExpression(@NotNull PsiCallExpression expression) {
      super.visitCallExpression(expression);
      if (!warnOnAllExceptions) {
        return;
      }
      final List<PsiClassType> exceptions = ExceptionUtil.getThrownExceptions(expression);
      if (exceptions.isEmpty()) {
        return;
      }
      for (PsiClassType exception : exceptions) {
        final PsiCodeBlock finallyBlock = getContainingFinallyBlock(expression, exception);
        if (finallyBlock != null && isHidingOfPreviousException(finallyBlock, expression)) {
          if (expression instanceof PsiMethodCallExpression) {
            registerMethodCallError((PsiMethodCallExpression)expression, exception);
          }
          else if (expression instanceof PsiNewExpression) {
            registerNewExpressionError((PsiNewExpression)expression, exception);
          }
          return;
        }
      }
    }

    @Override
    public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiExpression exception = PsiUtil.skipParenthesizedExprDown(statement.getException());
      if (exception == null) {
        return;
      }
      final PsiType type = exception.getType();
      if (type == null) {
        return;
      }
      final PsiCodeBlock finallyBlock = getContainingFinallyBlock(statement, type);
      if (finallyBlock == null) {
        return;
      }
      if (exception instanceof PsiReferenceExpression referenceExpression) {
        final PsiElement target = referenceExpression.resolve();
        if (target == null || !PsiTreeUtil.isAncestor(finallyBlock, target, true)) {
          // variable from outside finally block is thrown
          return;
        }
      }
      if (isHidingOfPreviousException(finallyBlock, statement)) {
        registerStatementError(statement);
      }
    }

    private boolean isHidingOfPreviousException(PsiCodeBlock finallyBlock, PsiElement throwElement) {
      final PsiElement parent = finallyBlock.getParent();
      if (!(parent instanceof PsiTryStatement tryStatement)) {
        // never reached
        return false;
      }
      final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
      if (catchBlocks.length == 0) {
        return true;
      }
      final PsiIfStatement ifStatement = getParentOfType(throwElement, PsiIfStatement.class, finallyBlock);
      if (ifStatement == null) {
        return true;
      }
      final boolean inThenBranch = PsiTreeUtil.isAncestor(ifStatement.getThenBranch(), throwElement, false);
      final boolean inElseBranch = PsiTreeUtil.isAncestor(ifStatement.getElseBranch(), throwElement, false);
      if (!inThenBranch && !inElseBranch) {
        return true;
      }
      final PsiExpression condition = ifStatement.getCondition();
      final PsiVariable variable = ExpressionUtils.getVariableFromNullComparison(condition, inThenBranch);
      if (variable == null) {
        return true;
      }
      boolean assigned = true;
      for (PsiCodeBlock catchBlock : catchBlocks) {
        assigned &= VariableAccessUtils.variableIsAssigned(variable, catchBlock);
      }
      return !assigned;
    }

    @Nullable
    public <T extends PsiElement> T getParentOfType(@Nullable PsiElement element, @NotNull Class<T> aClass, @NotNull PsiElement stopAt) {
      if (element == null || element instanceof PsiFile) return null;
      element = element.getParent();

      while (element != null && !aClass.isInstance(element)) {
        if (element == stopAt || element instanceof PsiFile) return null;
        element = element.getParent();
      }
      //noinspection unchecked
      return (T)element;
    }
  }

  private static PsiCodeBlock getContainingFinallyBlock(@NotNull PsiElement element, @NotNull PsiType thrownType) {
    PsiElement currentElement = element;
    while (true) {
      final PsiTryStatement tryStatement = PsiTreeUtil
        .getParentOfType(currentElement, PsiTryStatement.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (tryStatement == null) {
        return null;
      }
      final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (PsiTreeUtil.isAncestor(finallyBlock, currentElement, true)) {
        return finallyBlock;
      }
      if (PsiTreeUtil.isAncestor(tryStatement.getTryBlock(), currentElement, true) && isCaught(tryStatement, thrownType)) {
        return null;
      }
      currentElement = tryStatement;
    }
  }

  private static boolean isCaught(PsiTryStatement tryStatement, PsiType exceptionType) {
    for (PsiParameter parameter : tryStatement.getCatchBlockParameters()) {
      final PsiType type = parameter.getType();
      if (type.isAssignableFrom(exceptionType)) return true;
    }
    return false;
  }
}