/*
 * Copyright 2006-2024 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfLongType;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.lang.java.parser.JavaBinaryOperations;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantEvaluationOverflowException;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class IntegerMultiplicationImplicitCastToLongInspection extends BaseInspection implements CleanupLocalInspectionTool {
  private static final CallMatcher JUNIT4_ASSERT_EQUALS =
    CallMatcher.anyOf(
      CallMatcher.staticCall("org.junit.Assert", "assertEquals").parameterTypes("long", "long"),
      CallMatcher.staticCall("org.junit.Assert", "assertEquals").parameterTypes(CommonClassNames.JAVA_LANG_STRING, "long", "long")
    );

  private static final @NonNls Set<String> s_typesToCheck = Set.of(
    "int",
    "short",
    "byte",
    "char",
    CommonClassNames.JAVA_LANG_INTEGER,
    CommonClassNames.JAVA_LANG_SHORT,
    CommonClassNames.JAVA_LANG_BYTE,
    CommonClassNames.JAVA_LANG_CHARACTER
  );

  @SuppressWarnings("PublicField")
  public boolean ignoreNonOverflowingCompileTimeConstants = true;

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    return new IntegerMultiplicationImplicitCastToLongInspectionFix();
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final IElementType tokenType = (IElementType)infos[0];
    if (JavaTokenType.ASTERISK.equals(tokenType)) {
      return InspectionGadgetsBundle.message("integer.multiplication.implicit.cast.to.long.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("integer.shift.implicit.cast.to.long.problem.descriptor");
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreNonOverflowingCompileTimeConstants", InspectionGadgetsBundle.message(
        "integer.multiplication.implicit.cast.to.long.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IntegerMultiplicationImplicitlyCastToLongVisitor();
  }

  private static boolean isNonLongInteger(PsiType type) {
    if (type == null) return false;
    final String text = type.getCanonicalText();
    return s_typesToCheck.contains(text);
  }

  /**
   * Checks whether one of operands of polyadic expression itself is polyadic expression with multiplication operator.
   * For shift operations only first operand is considered.
   */
  private static boolean hasInnerMultiplication(@NotNull PsiPolyadicExpression expression) {
    final IElementType tokenType = expression.getOperationTokenType();
    if (isShiftToken(tokenType)) {
      return hasMultiplication(expression.getOperands()[0]);
    }

    return ContainerUtil.exists(expression.getOperands(), operand -> hasMultiplication(operand));
  }

  private static boolean hasMultiplication(PsiExpression expression) {
    expression = PsiUtil.deparenthesizeExpression(expression);

    if (expression instanceof PsiPrefixExpression) {
      return hasMultiplication(((PsiPrefixExpression)expression).getOperand());
    }

    if (expression instanceof PsiPolyadicExpression polyExpr) {
      final IElementType tokenType = polyExpr.getOperationTokenType();

      if (tokenType == JavaTokenType.ASTERISK) {
        return true;
      }

      return hasInnerMultiplication(polyExpr);
    }

    if (expression instanceof PsiConditionalExpression ternary) {
      return hasMultiplication(ternary.getThenExpression()) || hasMultiplication(ternary.getElseExpression());
    }

    return false;
  }

  private static boolean isShiftToken(IElementType tokenType) {
    return JavaBinaryOperations.SHIFT_OPS.contains(tokenType);
  }

  private static boolean isShiftEqToken(@NotNull IElementType tokenType) {
    return tokenType.equals(JavaTokenType.LTLTEQ) || tokenType.equals(JavaTokenType.GTGTEQ) ||
           tokenType.equals(JavaTokenType.GTGTGTEQ);
  }

  private static class IntegerMultiplicationImplicitCastToLongInspectionFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("integer.multiplication.implicit.cast.to.long.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiPolyadicExpression expression = (PsiPolyadicExpression)startElement;

      final PsiExpression[] operands = expression.getOperands();
      if (operands.length < 2) {
        return;
      }

      final PsiExpression exprToCast;
      if (operands.length > 2 || expression.getOperationTokenType() == JavaTokenType.LTLT) {
        exprToCast = operands[0];
      }
      else {
        exprToCast = Arrays.stream(operands)
          .map(operand -> PsiUtil.deparenthesizeExpression(operand))
          .filter(operand -> isIntegerLiteral(operand) ||
                             operand instanceof PsiPrefixExpression prefixExpr && isIntegerLiteral(prefixExpr.getOperand()))
          .findFirst()
          .orElse(operands[0]);
      }

      addCast(exprToCast);
    }

    private static boolean isIntegerLiteral(@Nullable PsiExpression operand) {
      return operand instanceof PsiLiteralExpression literal && literal.getValue() instanceof Integer;
    }

    private static void addCast(@NotNull PsiExpression expression) {
      if (expression instanceof PsiPrefixExpression) {
        final PsiExpression operand = ((PsiPrefixExpression)expression).getOperand();
        if (operand instanceof PsiLiteralExpression) expression = operand;
      }

      final String replacementText;
      if (isIntegerLiteral(expression)) {
        replacementText = expression.getText() + "L";
      }
      else {
        replacementText = "(long)" + expression.getText();
      }

      PsiReplacementUtil.replaceExpression(expression, replacementText);
    }
  }

  private class IntegerMultiplicationImplicitlyCastToLongVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.ASTERISK) && !tokenType.equals(JavaTokenType.LTLT)) {
        return;
      }
      final PsiType type = expression.getType();
      if (!isNonLongInteger(type)) {
        return;
      }
      if (hasInnerMultiplication(expression)) {
        return;
      }
      if (insideAssertEquals(expression)) {
        return;
      }
      PsiExpression[] operands = expression.getOperands();
      if (operands.length < 2 || expression.getLastChild() instanceof PsiErrorElement) {
        return;
      }
      PsiExpression context = getContainingExpression(expression);
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(context.getParent());
      if (parent instanceof PsiAssignmentExpression assignment && isShiftEqToken(assignment.getOperationTokenType()) &&
          PsiTreeUtil.isAncestor(assignment.getRExpression(), context, false)) {
        // like 'a <<= b * c'; 'b * c' should not be reported even if 'a' is long
        return;
      }
      if (parent instanceof PsiTypeCastExpression cast) {
        PsiType castType = cast.getType();
        if (isNonLongInteger(castType)) return;
        if (PsiTypes.longType().equals(castType)) context = cast;
      }
      if (!PsiTypes.longType().equals(context.getType()) &&
          !PsiTypes.longType().equals(ExpectedTypeUtils.findExpectedType(context, true))) {
        return;
      }
      if (ignoreNonOverflowingCompileTimeConstants) {
        try {
          if (ExpressionUtils.computeConstantExpression(expression, true) != null) {
            return;
          }
        }
        catch (ConstantEvaluationOverflowException ignore) {
        }
        if (cannotOverflow(expression, operands, tokenType.equals(JavaTokenType.LTLT))) {
          return;
        }
      }
      registerError(expression, tokenType);
    }

    private static boolean insideAssertEquals(PsiExpression expression) {
      PsiElement parent = ExpressionUtils.getPassThroughParent(expression);
      if (parent instanceof PsiExpressionList) {
        PsiMethodCallExpression call = ObjectUtils.tryCast(parent.getParent(), PsiMethodCallExpression.class);
        // JUnit4 has assertEquals(long, long) but no assertEquals(int, int)
        // so int-assertions will be wired to assertEquals(long, long), which could be annoying false-positive.
        // If the multiplication unexpectedly overflows then assertion will fail anyway, so the problem will manifest itself.
        return JUNIT4_ASSERT_EQUALS.matches(call);
      }
      return false;
    }

    private static boolean cannotOverflow(@NotNull PsiPolyadicExpression expression, PsiExpression[] operands, boolean shift) {
      CommonDataflow.DataflowResult dfr = CommonDataflow.getDataflowResult(expression);
      if (dfr != null) {
        long min = 1, max = 1;
        for (PsiExpression operand : operands) {
          LongRangeSet set = DfLongType.extractRange(dfr.getDfType(PsiUtil.skipParenthesizedExprDown(operand)));
          if (operand == operands[0]) {
            min = set.min();
            max = set.max();
            continue;
          }
          long r1, r2, r3, r4;
          if (shift) {
            set = set.bitwiseAnd(LongRangeSet.point(0x3F));
            long nextMin = set.min();
            long nextMax = set.max();
            if (nextMax >= 0x20) return false;
            r1 = min << nextMin;
            r2 = max << nextMin;
            r3 = min << nextMax;
            r4 = max << nextMax;
          }
          else {
            long nextMin = set.min();
            long nextMax = set.max();
            if (intOverflow(nextMin) || intOverflow(nextMax)) return false;
            r1 = min * nextMin;
            r2 = max * nextMin;
            r3 = min * nextMax;
            r4 = max * nextMax;
          }
          if (intOverflow(r1) || intOverflow(r2) || intOverflow(r3) || intOverflow(r4)) return false;
          min = Math.min(Math.min(r1, r2), Math.min(r3, r4));
          max = Math.max(Math.max(r1, r2), Math.max(r3, r4));
        }
      }
      return true;
    }

    private static boolean intOverflow(long l) {
      return (int)l != l && l != Integer.MAX_VALUE + 1L;
    }

    private static PsiExpression getContainingExpression(PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiPolyadicExpression polyParent && TypeConversionUtil.isNumericType(polyParent.getType())) {
        final IElementType tokenType = polyParent.getOperationTokenType();
        if (!isShiftToken(tokenType) || expression == polyParent.getOperands()[0]) {
          return getContainingExpression(polyParent);
        }
      }
      if (parent instanceof PsiParenthesizedExpression ||
          parent instanceof PsiPrefixExpression ||
          parent instanceof PsiConditionalExpression) {
        return getContainingExpression((PsiExpression)parent);
      }
      return expression;
    }
  }
}