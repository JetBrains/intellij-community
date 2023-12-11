/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ManualArrayCopyInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("manual.array.copy.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ManualArrayCopyVisitor();
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new ManualArrayCopyFix();
  }

  private static class ManualArrayCopyFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "System.arraycopy()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement forElement, @NotNull ModPsiUpdater updater) {
      final PsiForStatement forStatement = (PsiForStatement)forElement.getParent();
      CommentTracker commentTracker = new CommentTracker();
      final String newExpression = buildSystemArrayCopyText(forStatement, commentTracker);
      if (newExpression == null) {
        return;
      }
      PsiIfStatement ifStatement = (PsiIfStatement)commentTracker.replaceAndRestoreComments(forStatement, newExpression);
      if (Boolean.TRUE.equals(DfaUtil.evaluateCondition(ifStatement.getCondition())))  {
        PsiStatement copyStatement = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
        assert copyStatement != null;
        new CommentTracker().replaceAndRestoreComments(ifStatement, copyStatement);
      }
    }

    private static @Nullable @NonNls String buildSystemArrayCopyText(PsiForStatement forStatement, CommentTracker commentTracker) {
      final CountingLoop countingLoop = CountingLoop.from(forStatement);
      if (countingLoop == null) {
        return null;
      }
      final PsiExpression limit = countingLoop.getBound();
      final PsiExpression initializer = countingLoop.getInitializer();
      final String lengthText =
        countingLoop.isDescending()
        ? buildLengthText(initializer, limit, countingLoop.isIncluding(), commentTracker)
        : buildLengthText(limit, initializer, countingLoop.isIncluding(), commentTracker);
      if (lengthText == null) {
        return null;
      }
      final PsiArrayAccessExpression lhs = getLhsArrayAccessExpression(forStatement);
      if (lhs == null) {
        return null;
      }
      final PsiExpression lArray = lhs.getArrayExpression();
      final String toArrayText = commentTracker.text(lArray);
      final PsiArrayAccessExpression rhs = getRhsArrayAccessExpression(forStatement);
      if (rhs == null) {
        return null;
      }
      final PsiExpression rArray = rhs.getArrayExpression();
      final String fromArrayText = commentTracker.text(rArray);
      final PsiExpression rhsIndexExpression = PsiUtil.skipParenthesizedExprDown(rhs.getIndexExpression());
      final PsiExpression limitExpression = countingLoop.isDescending() ? limit : initializer;
      final String fromOffsetText = buildOffsetText(rhsIndexExpression, countingLoop.getCounter(), limitExpression,
                                                    countingLoop.isDescending() && !countingLoop.isIncluding(), commentTracker);
      final PsiExpression lhsIndexExpression = PsiUtil.skipParenthesizedExprDown(lhs.getIndexExpression());
      final String toOffsetText = buildOffsetText(lhsIndexExpression, countingLoop.getCounter(), limitExpression,
                                                  countingLoop.isDescending() && !countingLoop.isIncluding(), commentTracker);
      return "if(" + lengthText + ">=0)" +
             "System.arraycopy(" + fromArrayText + "," + fromOffsetText + "," + toArrayText + "," + toOffsetText + "," + lengthText + ");";
    }

    @Nullable
    private static PsiArrayAccessExpression getLhsArrayAccessExpression(PsiForStatement forStatement) {
      PsiStatement body = forStatement.getBody();
      while (body instanceof PsiBlockStatement blockStatement) {
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 2) {
          body = statements[1];
        }
        else if (statements.length == 1) {
          body = statements[0];
        }
        else {
          return null;
        }
      }
      if (!(body instanceof PsiExpressionStatement expressionStatement)) {
        return null;
      }
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiAssignmentExpression assignmentExpression)) {
        return null;
      }
      final PsiExpression lhs = assignmentExpression.getLExpression();
      final PsiExpression deparenthesizedExpression = PsiUtil.skipParenthesizedExprDown(lhs);
      if (!(deparenthesizedExpression instanceof PsiArrayAccessExpression)) {
        return null;
      }
      return (PsiArrayAccessExpression)deparenthesizedExpression;
    }

    @Nullable
    private static PsiArrayAccessExpression getRhsArrayAccessExpression(PsiForStatement forStatement) {
      PsiStatement body = forStatement.getBody();
      while (body instanceof PsiBlockStatement blockStatement) {
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 1 || statements.length == 2) {
          body = statements[0];
        }
        else {
          return null;
        }
      }
      final PsiExpression arrayAccessExpression;
      if (body instanceof PsiDeclarationStatement declarationStatement) {
        final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        if (declaredElements.length != 1) {
          return null;
        }
        final PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable variable)) {
          return null;
        }
        arrayAccessExpression = variable.getInitializer();
      }
      else if (body instanceof PsiExpressionStatement expressionStatement) {
        final PsiExpression expression = expressionStatement.getExpression();
        if (!(expression instanceof PsiAssignmentExpression assignmentExpression)) {
          return null;
        }
        arrayAccessExpression = assignmentExpression.getRExpression();
      }
      else {
        return null;
      }
      final PsiExpression unparenthesizedExpression = PsiUtil.skipParenthesizedExprDown(arrayAccessExpression);
      if (!(unparenthesizedExpression instanceof PsiArrayAccessExpression)) {
        return null;
      }
      return (PsiArrayAccessExpression)unparenthesizedExpression;
    }

    @NonNls
    @Nullable
    private static String buildLengthText(PsiExpression max, PsiExpression min, boolean plusOne, CommentTracker commentTracker) {
      max = PsiUtil.skipParenthesizedExprDown(max);
      if (max == null) {
        return null;
      }
      min = PsiUtil.skipParenthesizedExprDown(min);
      if (min == null) {
        return buildExpressionText(max, plusOne, commentTracker);
      }
      final Object minConstant = ExpressionUtils.computeConstantExpression(min);
      if (minConstant instanceof Number minNumber) {
        final int minValue = plusOne ? minNumber.intValue() - 1 : minNumber.intValue();
        if (minValue == 0) {
          return buildExpressionText(max, false, commentTracker);
        }
        if (max instanceof PsiLiteralExpression) {
          final Object maxConstant = ExpressionUtils.computeConstantExpression(max);
          if (maxConstant instanceof Number number) {
            return String.valueOf(number.intValue() - minValue);
          }
        }
        final String maxText = buildExpressionText(max, false, commentTracker);
        return minValue > 0
               ? maxText + '-' + minValue
               : maxText + '+' + -minValue;
      }
      // - 1 because of the increment inside the com.siyeh.ig.psiutils.CommentTracker.text(com.intellij.psi.PsiExpression, int)
      final String minText = commentTracker.text(min, ParenthesesUtils.ADDITIVE_PRECEDENCE - 1);
      final String maxText = buildExpressionText(max, plusOne, commentTracker);
      return maxText + '-' + minText;
    }

    private static String buildExpressionText(PsiExpression expression, boolean plusOne, CommentTracker commentTracker) {
      return plusOne
             ? JavaPsiMathUtil.add(expression, 1, commentTracker)
             : commentTracker.text(expression, ParenthesesUtils.ADDITIVE_PRECEDENCE);
    }

    @NonNls
    @Nullable
    private static String buildOffsetText(PsiExpression expression,
                                          PsiLocalVariable variable,
                                          PsiExpression limitExpression,
                                          boolean plusOne,
                                          CommentTracker commentTracker) {
      if (expression == null) {
        return null;
      }
      final String expressionText = commentTracker.text(expression);
      final String variableName = variable.getName();
      if (expressionText.equals(variableName)) {
        final PsiExpression initialValue = PsiUtil.skipParenthesizedExprDown(limitExpression);
        if (initialValue == null) {
          return null;
        }
        return buildExpressionText(initialValue, plusOne, commentTracker);
      }
      else if (expression instanceof PsiBinaryExpression binaryExpression) {
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        final String rhsText = buildOffsetText(rhs, variable, limitExpression, plusOne, commentTracker);
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if (ExpressionUtils.isZero(lhs)) {
          return tokenType.equals(JavaTokenType.MINUS) ? '-' + rhsText : rhsText;
        }
        if (plusOne && tokenType.equals(JavaTokenType.MINUS) && ExpressionUtils.isOne(rhs)) {
          return buildOffsetText(lhs, variable, limitExpression, false, commentTracker);
        }
        final String lhsText = buildOffsetText(lhs, variable, limitExpression, plusOne, commentTracker);
        if (ExpressionUtils.isZero(rhs)) {
          return lhsText;
        }
        return collapseConstant(lhsText + sign.getText() + rhsText, variable);
      }
      return collapseConstant(commentTracker.text(expression), variable);
    }

    private static String collapseConstant(@NonNls String expressionText, PsiElement context) {
      final Project project = context.getProject();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiExpression fromOffsetExpression = factory.createExpressionFromText(expressionText, context);
      final Object fromOffsetConstant = ExpressionUtils.computeConstantExpression(fromOffsetExpression);
      if (fromOffsetConstant != null) {
        return fromOffsetConstant.toString();
      }
      else {
        return expressionText;
      }
    }
  }

  private static class ManualArrayCopyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      final CountingLoop countingLoop = CountingLoop.from(statement);
      if (countingLoop == null) {
        return;
      }
      final PsiStatement body = statement.getBody();
      if (!bodyIsArrayCopy(body, countingLoop.getCounter())) {
        return;
      }
      registerStatementError(statement);
    }

    private static boolean bodyIsArrayCopy(PsiStatement body, PsiVariable variable) {
      if (body instanceof PsiExpressionStatement exp) {
        final PsiExpression expression = exp.getExpression();
        return expressionIsArrayCopy(expression, variable);
      }
      else if (body instanceof PsiBlockStatement blockStatement) {
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 1) {
          return bodyIsArrayCopy(statements[0], variable);
        }
        else if (statements.length == 2) {
          final PsiStatement statement = statements[0];
          if (!(statement instanceof PsiDeclarationStatement declarationStatement)) {
            return false;
          }
          final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
          if (declaredElements.length != 1) {
            return false;
          }
          final PsiElement declaredElement = declaredElements[0];
          if (!(declaredElement instanceof PsiVariable localVariable)) {
            return false;
          }
          final PsiExpression initializer = localVariable.getInitializer();
          if (!ExpressionUtils.isOffsetArrayAccess(initializer, variable)) {
            return false;
          }
          return bodyIsArrayCopy(statements[1], variable);
        }
      }
      return false;
    }

    private static boolean expressionIsArrayCopy(@Nullable PsiExpression expression, @NotNull PsiVariable variable) {
      final PsiExpression strippedExpression = PsiUtil.skipParenthesizedExprDown(expression);
      if (strippedExpression == null) {
        return false;
      }
      if (!(strippedExpression instanceof PsiAssignmentExpression assignment)) {
        return false;
      }
      final IElementType tokenType = assignment.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.EQ)) {
        return false;
      }
      final PsiExpression lhs = assignment.getLExpression();
      if (SideEffectChecker.mayHaveSideEffects(lhs)) {
        return false;
      }
      if (!ExpressionUtils.isOffsetArrayAccess(lhs, variable)) {
        return false;
      }
      final PsiExpression rhs = assignment.getRExpression();
      if (rhs == null) {
        return false;
      }
      if (SideEffectChecker.mayHaveSideEffects(rhs)) {
        return false;
      }
      if (!areExpressionsCopyable(lhs, rhs)) {
        return false;
      }
      final PsiType type = lhs.getType();
      if (type instanceof PsiPrimitiveType) {
        final PsiExpression strippedLhs = PsiUtil.skipParenthesizedExprDown(lhs);
        final PsiExpression strippedRhs = PsiUtil.skipParenthesizedExprDown(rhs);
        if (!areExpressionsCopyable(strippedLhs, strippedRhs)) {
          return false;
        }
      }
      if (!ExpressionUtils.isOffsetArrayAccess(rhs, variable)) {
        return false;
      }
      return !isSameSourceAndDestination(lhs, rhs);
    }

    private static boolean isSameSourceAndDestination(PsiExpression lhs, PsiExpression rhs) {
      lhs = PsiUtil.skipParenthesizedExprDown(lhs);
      rhs = PsiUtil.skipParenthesizedExprDown(rhs);
      assert lhs != null && rhs != null;
      /// checked in ExpressionUtils.isOffsetArrayAccess()
      PsiArrayAccessExpression left = (PsiArrayAccessExpression)lhs;
      PsiArrayAccessExpression right = (PsiArrayAccessExpression)rhs;
      return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(left.getArrayExpression(), right.getArrayExpression());
    }

    private static boolean areExpressionsCopyable(@Nullable PsiExpression lhs, @Nullable PsiExpression rhs) {
      if (lhs == null || rhs == null) {
        return false;
      }
      final PsiType lhsType = lhs.getType();
      if (lhsType == null) {
        return false;
      }
      final PsiType rhsType = rhs.getType();
      if (rhsType == null) {
        return false;
      }
      if (lhsType instanceof PsiPrimitiveType) {
        if (!lhsType.equals(rhsType)) {
          return false;
        }
      }
      else {
        if (!lhsType.isAssignableFrom(rhsType) || rhsType instanceof PsiPrimitiveType) {
          return false;
        }
      }
      return true;
    }
  }
}