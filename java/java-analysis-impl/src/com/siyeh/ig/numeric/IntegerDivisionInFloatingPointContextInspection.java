/*
 * Copyright 2003-2024 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class IntegerDivisionInFloatingPointContextInspection extends BaseInspection {

  static final @NonNls Set<String> s_integralTypes = Set.of(
    "int",
    "long",
    "short",
    "byte",
    "char",
    CommonClassNames.JAVA_LANG_INTEGER,
    CommonClassNames.JAVA_LANG_LONG,
    CommonClassNames.JAVA_LANG_SHORT,
    CommonClassNames.JAVA_LANG_BYTE,
    CommonClassNames.JAVA_LANG_CHARACTER
  );

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("integer.division.in.floating.point.context.problem.descriptor");
  }

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    String castTo = (String)infos[0];
    return new IntegerDivisionInFloatingPointContextFix(castTo);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IntegerDivisionInFloatingPointContextVisitor();
  }

  private static class IntegerDivisionInFloatingPointContextVisitor extends BaseInspectionVisitor {

    IntegerDivisionInFloatingPointContextVisitor() { }

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.DIV)) {
        return;
      }
      if (!hasIntegerDivision(expression)) {
        return;
      }
      final PsiExpression context = getContainingExpression(expression);
      PsiType contextType = ExpectedTypeUtils.findExpectedType(context, true);
      if (contextType == null) {
        contextType = context.getType();
      }
      if (!PsiTypes.floatType().equals(contextType) && !PsiTypes.doubleType().equals(contextType)) {
        return;
      }
      registerError(expression, contextType.getCanonicalText());
    }

    private static boolean hasIntegerDivision(@NotNull PsiPolyadicExpression expression) {
      final PsiExpression[] operands = expression.getOperands();
      return operands.length >= 2 && isIntegral(operands[0].getType()) && isIntegral(operands[1].getType());
    }

    private static boolean isIntegral(PsiType type) {
      return type != null && s_integralTypes.contains(type.getCanonicalText());
    }
  }

  private static class IntegerDivisionInFloatingPointContextFix extends PsiUpdateModCommandQuickFix {
    private final String myCastTo;

    private IntegerDivisionInFloatingPointContextFix(String castTo) {
      myCastTo = castTo;
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      if (!(startElement instanceof PsiPolyadicExpression expression)) {
        return;
      }
      PsiExpression[] operands = expression.getOperands();
      if (operands.length < 1) return;
      PsiExpression operand = operands[0];
      CommentTracker tracker = new CommentTracker();
      String text = tracker.text(operand, ParenthesesUtils.TYPE_CAST_PRECEDENCE);
      tracker.replace(operand, "(" + myCastTo + ")" + text);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("integer.division.in.floating.point.context.fix.family.name");
    }

    @Override
    public @NotNull String getName() {
      return InspectionGadgetsBundle.message("integer.division.in.floating.point.context.fix.name", myCastTo);
    }
  }

  private static @NotNull PsiExpression getContainingExpression(@NotNull PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiBinaryExpression binaryExpression) {
      return !ComparisonUtils.isComparisonOperation(binaryExpression.getOperationTokenType())
             ? getContainingExpression(binaryExpression)
             : expression;
    }
    else if (parent instanceof PsiPolyadicExpression ||
             parent instanceof PsiParenthesizedExpression ||
             parent instanceof PsiPrefixExpression ||
             parent instanceof PsiConditionalExpression) {
      return getContainingExpression((PsiExpression)parent);
    }
    return expression;
  }
}