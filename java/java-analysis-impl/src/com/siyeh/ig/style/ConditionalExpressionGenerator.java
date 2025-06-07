// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Helper class to generate (and possibly simplify) conditional expression based on the model
 */
public final class ConditionalExpressionGenerator {
  private final @NotNull String myTokenType;
  private final @NotNull Function<@NotNull CommentTracker, @NotNull String> myGenerator;
  private final @Nullable PsiExpression myReplacement;

  private ConditionalExpressionGenerator(@NotNull String type, @NotNull Function<@NotNull CommentTracker, @NotNull String> generator) {
    myTokenType = type;
    myGenerator = generator;
    myReplacement = null;
  }

  private ConditionalExpressionGenerator(@NotNull String type, @NotNull PsiExpression replacement) {
    myTokenType = type;
    myGenerator = ct -> ct.text(replacement);
    myReplacement = replacement;
  }

  /**
   * @return a physical replacement expression if the condition could be replaced by some subexpression,
   * null if target expression must be non-trivially generated
   */
  public @Nullable PsiExpression getReplacement() {
    return myReplacement;
  }

  /**
   * @return a textual representation of the resulting operator that will be used in the conditional expression
   */
  public @NotNull String getTokenType() {
    return myTokenType;
  }

  /**
   * @param ct CommentTracker to use
   * @return a text of generated conditional expression
   */
  public @NotNull String generate(CommentTracker ct) {
    return myGenerator.apply(ct);
  }

  /**
   * @param model model to create a generator from
   * @return generator
   */
  public static ConditionalExpressionGenerator from(ConditionalModel model) {
    PsiExpression condition = model.getCondition();
    PsiExpression thenExpression = model.getThenExpression();
    PsiExpression elseExpression = model.getElseExpression();
    if (PsiTypes.booleanType().equals(model.getType()) || model.getType().equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
      PsiLiteralExpression thenLiteral = ExpressionUtils.getLiteral(thenExpression);
      PsiLiteralExpression elseLiteral = ExpressionUtils.getLiteral(elseExpression);
      Boolean thenValue = thenLiteral == null ? null : tryCast(thenLiteral.getValue(), Boolean.class);
      Boolean elseValue = elseLiteral == null ? null : tryCast(elseLiteral.getValue(), Boolean.class);
      if (thenValue != null && elseValue != null) {
        if (thenValue.equals(elseValue)) {
          // Equal branches are handled by separate inspections
          return null;
        }
        if (thenValue) {
          return new ConditionalExpressionGenerator("", condition);
        }
        return new ConditionalExpressionGenerator("", ct -> BoolUtils.getNegatedExpressionText(condition, ct));
      }
      if ((thenValue != null || elseValue != null) && PsiTypes.booleanType().equals(model.getType())) {
        return getAndOrGenerator(condition, thenExpression, elseExpression, thenValue, elseValue);
      }
      if (BoolUtils.areExpressionsOpposite(thenExpression, elseExpression)) {
        return getEqualityGenerator(model.getCondition(), thenExpression);
      }
    }
    PsiExpression redundantComparisonReplacement = getRedundantComparisonReplacement(model);
    if (redundantComparisonReplacement != null) {
      return new ConditionalExpressionGenerator("", redundantComparisonReplacement);
    }
    return new ConditionalExpressionGenerator("?:", ct -> generateTernary(ct, condition, thenExpression, elseExpression, model.getType()));
  }

  private static ConditionalExpressionGenerator getAndOrGenerator(PsiExpression condition,
                                                                  PsiExpression thenExpression,
                                                                  PsiExpression elseExpression,
                                                                  Boolean thenValue,
                                                                  Boolean elseValue) {
    if (thenValue != null) {
      if (thenValue) {
        return new ConditionalExpressionGenerator("||", ct -> joinConditions(condition, elseExpression, false, ct));
      }
      return new ConditionalExpressionGenerator("&&", ct ->
        BoolUtils.getNegatedExpressionText(condition, ParenthesesUtils.AND_PRECEDENCE, ct) + " && " +
        ct.text(elseExpression, ParenthesesUtils.AND_PRECEDENCE));
    }
    if (!elseValue) {
      return new ConditionalExpressionGenerator("&&", ct -> joinConditions(condition, thenExpression, true, ct));
    }
    return new ConditionalExpressionGenerator("||", ct ->
      BoolUtils.getNegatedExpressionText(condition, ParenthesesUtils.OR_PRECEDENCE, ct) + " || " +
      ct.text(thenExpression, ParenthesesUtils.OR_PRECEDENCE));
  }

  private static PsiExpression getRedundantComparisonReplacement(@NotNull ConditionalModel model) {
    @NotNull PsiExpression thenExpression = model.getThenExpression();
    @NotNull PsiExpression elseExpression = model.getElseExpression();
    PsiBinaryExpression binOp = tryCast(PsiUtil.skipParenthesizedExprDown(model.getCondition()), PsiBinaryExpression.class);
    if (binOp == null) return null;
    IElementType tokenType = binOp.getOperationTokenType();
    boolean equals = tokenType.equals(JavaTokenType.EQEQ);
    if (!equals && !tokenType.equals(JavaTokenType.NE)) return null;
    PsiExpression left = PsiUtil.skipParenthesizedExprDown(binOp.getLOperand());
    PsiExpression right = PsiUtil.skipParenthesizedExprDown(binOp.getROperand());
    if (!ExpressionUtils.isSafelyRecomputableExpression(left) || !ExpressionUtils.isSafelyRecomputableExpression(right)) return null;
    if (TypeConversionUtil.isFloatOrDoubleType(left.getType()) && TypeConversionUtil.isFloatOrDoubleType(right.getType())) {
      // Simplifying the comparison of two floats/doubles like "if(a == 0.0) return 0.0; else return a;" 
      // will cause a semantics change for "a == -0.0" 
      return null;
    }
    EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
    if (equivalence.expressionsAreEquivalent(left, thenExpression) && equivalence.expressionsAreEquivalent(right, elseExpression) ||
        equivalence.expressionsAreEquivalent(right, thenExpression) && equivalence.expressionsAreEquivalent(left, elseExpression)) {
      return equals ? elseExpression : thenExpression;
    }
    return null;
  }

  private static ConditionalExpressionGenerator getEqualityGenerator(PsiExpression condition, PsiExpression expression) {
    boolean equal = true;
    PsiExpression left, right;
    if (BoolUtils.isNegation(condition)) {
      equal = false;
      left = Objects.requireNonNull(BoolUtils.getNegated(condition));
    }
    else {
      left = condition;
    }
    if (BoolUtils.isNegation(expression)) {
      equal = !equal;
      right = Objects.requireNonNull(BoolUtils.getNegated(expression));
    }
    else {
      right = expression;
    }
    String token = equal ? "==" : "!=";
    // RELATIONAL_PRECEDENCE is technically enough here, but it may produces quite confusing code like "a == b > c"
    // so we add (formally redundant) parentheses in this case: "a == (b > c)"
    return new ConditionalExpressionGenerator(token, ct ->
      ct.text(left, PsiPrecedenceUtil.SHIFT_PRECEDENCE) + " " + token + " " + ct.text(right, PsiPrecedenceUtil.SHIFT_PRECEDENCE));
  }

  private static PsiExpression expandDiamondsWhenNeeded(PsiExpression thenValue, PsiType requiredType) {
    if (thenValue instanceof PsiNewExpression) {
      if (!PsiDiamondTypeUtil.canChangeContextForDiamond((PsiNewExpression)thenValue, requiredType)) {
        return PsiDiamondTypeUtil.expandTopLevelDiamondsInside(thenValue);
      }
    }
    return thenValue;
  }

  private static String generateTernary(CommentTracker ct,
                                        PsiExpression condition,
                                        PsiExpression thenValue,
                                        PsiExpression elseValue,
                                        PsiType type) {
    thenValue = expandDiamondsWhenNeeded(thenValue, type);
    elseValue = expandDiamondsWhenNeeded(elseValue, type);
    final @NonNls StringBuilder conditional = new StringBuilder();
    final String conditionText = ct.text(condition, ParenthesesUtils.CONDITIONAL_PRECEDENCE);
    if (condition instanceof PsiConditionalExpression) {
      conditional.append('(').append(conditionText).append(')');
    }
    else {
      conditional.append(conditionText);
    }
    conditional.append('?');
    final PsiType thenType = thenValue.getType();
    final PsiType elseType = elseValue.getType();
    if (thenType instanceof PsiPrimitiveType primitiveType &&
        !PsiTypes.nullType().equals(thenType) &&
        !(elseType instanceof PsiPrimitiveType) &&
        !(type instanceof PsiPrimitiveType)) {
      // prevent unboxing of boxed value to preserve semantics (IDEA-48267, IDEA-310641)
      conditional.append(primitiveType.getBoxedTypeName());
      conditional.append(".valueOf(").append(ct.text(thenValue)).append("):");
      conditional.append(ct.text(elseValue, ParenthesesUtils.CONDITIONAL_PRECEDENCE));
    }
    else if (elseType instanceof PsiPrimitiveType primitiveType &&
             !PsiTypes.nullType().equals(elseType) &&
             !(thenType instanceof PsiPrimitiveType) &&
             !(type instanceof PsiPrimitiveType)) {
      // prevent unboxing of boxed value to preserve semantics (IDEA-48267, IDEA-310641)
      conditional.append(ct.text(thenValue, ParenthesesUtils.CONDITIONAL_PRECEDENCE));
      conditional.append(':');
      conditional.append(primitiveType.getBoxedTypeName());
      conditional.append(".valueOf(").append(ct.text(elseValue)).append(')');
    }
    else {
      conditional.append(ct.text(thenValue, ParenthesesUtils.CONDITIONAL_PRECEDENCE));
      conditional.append(':');
      conditional.append(ct.text(elseValue, ParenthesesUtils.CONDITIONAL_PRECEDENCE));
    }
    return conditional.toString();
  }

  private static @NotNull String joinConditions(PsiExpression left, PsiExpression right, boolean isAnd, CommentTracker ct) {
    int precedence;
    String token;
    IElementType tokenType;
    if (isAnd) {
      precedence = ParenthesesUtils.AND_PRECEDENCE;
      token = " && ";
      tokenType = JavaTokenType.ANDAND;
    }
    else {
      precedence = ParenthesesUtils.OR_PRECEDENCE;
      token = " || ";
      tokenType = JavaTokenType.OROR;
    }
    PsiPolyadicExpression leftPolyadic = tryCast(PsiUtil.skipParenthesizedExprDown(left), PsiPolyadicExpression.class);
    PsiPolyadicExpression rightPolyadic = tryCast(PsiUtil.skipParenthesizedExprDown(right), PsiPolyadicExpression.class);
    // foo && (foo && bar) -> foo && bar
    if (rightPolyadic != null && rightPolyadic.getOperationTokenType().equals(tokenType) &&
        EquivalenceChecker.getCanonicalPsiEquivalence()
          .expressionsAreEquivalent(ArrayUtil.getFirstElement(rightPolyadic.getOperands()), left) &&
        !SideEffectChecker.mayHaveSideEffects(left)) {
      return ct.text(rightPolyadic);
    }
    // (foo && bar) && bar -> foo && bar
    if (leftPolyadic != null && leftPolyadic.getOperationTokenType().equals(tokenType) &&
        EquivalenceChecker.getCanonicalPsiEquivalence()
          .expressionsAreEquivalent(ArrayUtil.getLastElement(leftPolyadic.getOperands()), right) &&
        !SideEffectChecker.mayHaveSideEffects(right)) {
      return ct.text(leftPolyadic);
    }
    return ct.text(left, precedence) + token + ct.text(right, precedence);
  }
}
