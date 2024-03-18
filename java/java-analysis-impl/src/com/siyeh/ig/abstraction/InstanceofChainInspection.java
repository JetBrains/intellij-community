/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class InstanceofChainInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreInstanceofOnLibraryClasses = false;

  @Override
  @NotNull
  public String getID() {
    return "ChainOfInstanceofChecks";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Check check = (Check)infos[0];
    if (check == Check.CLASS_EQUALITY) {
      return InspectionGadgetsBundle.message("chain.of.class.equality.checks.problem.descriptor");
    }
    return InspectionGadgetsBundle.message("chain.of.instanceof.checks.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreInstanceofOnLibraryClasses", InspectionGadgetsBundle.message("ignore.instanceof.on.library.classes")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InstanceofChainVisitor();
  }

  private enum Check {
    CLASS_EQUALITY, INSTANCEOF, NEITHER
  }

  private class InstanceofChainVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement ifStatement) {
      super.visitIfStatement(ifStatement);
      if (ControlFlowUtils.isElseIf(ifStatement)) {
        return;
      }
      final PsiStatement previousStatement = PsiTreeUtil.getPrevSiblingOfType(ifStatement, PsiStatement.class);
      if (previousStatement instanceof PsiIfStatement previousIfStatement) {
        final PsiExpression condition = previousIfStatement.getCondition();
        if (chainCheck(condition, null) != Check.NEITHER) {
          return;
        }
      }
      int numChecks = 0;
      PsiIfStatement branch = ifStatement;
      Check check = null;
      while (true) {
        final PsiExpression condition = branch.getCondition();
        final Check chainCheck = chainCheck(condition, check);
        if (chainCheck == Check.NEITHER) {
          if (numChecks > 1) {
            break;
          }
          return;
        }
        else {
          check = chainCheck;
        }
        numChecks++;
        final PsiStatement elseBranch = branch.getElseBranch();
        if (elseBranch instanceof PsiIfStatement) {
          branch = (PsiIfStatement)elseBranch;
        }
        else if (elseBranch == null) {
          final PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(branch, PsiStatement.class);
          if (!(nextStatement instanceof PsiIfStatement)) {
            break;
          }
          branch = (PsiIfStatement)nextStatement;
        }
        else {
          break;
        }
      }
      if (numChecks < 2) {
        return;
      }
      registerStatementError(ifStatement, check);
    }

    private Check chainCheck(PsiExpression condition, Check check) {
      while (true) {
        if (condition == null) {
          return Check.NEITHER;
        }
        else if (check != Check.CLASS_EQUALITY && isInstanceofExpression(condition)) {
          return Check.INSTANCEOF;
        }
        else if (condition instanceof PsiPolyadicExpression polyadicExpression) {
          if (check != Check.INSTANCEOF && isClassEqualityExpression(condition)) {
            return Check.CLASS_EQUALITY;
          }
          final PsiExpression[] operands = polyadicExpression.getOperands();
          for (PsiExpression operand : operands) {
            final Check chainCheck = chainCheck(operand, check);
            if (chainCheck != Check.NEITHER) {
              return chainCheck;
            }
          }
          return Check.NEITHER;
        }
        else if (condition instanceof PsiParenthesizedExpression parenthesizedExpression) {
          condition = parenthesizedExpression.getExpression();
          continue;
        }
        else if (condition instanceof PsiUnaryExpression unaryOperation) {
          condition = unaryOperation.getOperand();
          continue;
        }
        return Check.NEITHER;
      }
    }

    private boolean isClassEqualityExpression(PsiExpression expression) {
      if (!(expression instanceof PsiBinaryExpression binaryExpression)) {
        return false;
      }
      if (binaryExpression.getOperationTokenType() != JavaTokenType.EQEQ) {
        return false;
      }
      return isClassObjectAccessExpression(binaryExpression.getLOperand()) ||
             isClassObjectAccessExpression(binaryExpression.getROperand());
    }

    private boolean isClassObjectAccessExpression(PsiExpression expression) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (!(expression instanceof PsiClassObjectAccessExpression classObjectAccessExpression)) {
        return false;
      }
      final PsiTypeElement typeElement = classObjectAccessExpression.getOperand();
      return !ignoreInstanceofOnLibraryClasses || !LibraryUtil.isTypeInLibrary(typeElement.getType());
    }

    private boolean isInstanceofExpression(PsiExpression expression) {
      if (!(expression instanceof PsiInstanceOfExpression instanceOfExpression)) {
        return false;
      }
      final PsiTypeElement typeElement = instanceOfExpression.getCheckType();
      return !ignoreInstanceofOnLibraryClasses || typeElement == null || !LibraryUtil.isTypeInLibrary(typeElement.getType());
    }
  }
}