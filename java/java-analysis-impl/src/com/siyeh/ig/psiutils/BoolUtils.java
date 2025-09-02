/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static com.intellij.codeInspection.util.OptionalUtil.*;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_OBJECTS;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_OPTIONAL;
import static com.intellij.psi.JavaTokenType.*;

public final class BoolUtils {

  private BoolUtils() { }

  public static boolean isNegation(@NotNull PsiExpression expression) {
    if (!(expression instanceof PsiPrefixExpression prefixExp)) {
      return false;
    }
    final IElementType tokenType = prefixExp.getOperationTokenType();
    return EXCL.equals(tokenType);
  }

  public static boolean isNegated(PsiExpression exp) {
    PsiExpression ancestor = exp;
    PsiElement parent = ancestor.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      ancestor = (PsiExpression)parent;
      parent = ancestor.getParent();
    }
    return parent instanceof PsiExpression && isNegation((PsiExpression)parent);
  }

  public static @Nullable PsiExpression getNegated(PsiExpression expression) {
    if (!(expression instanceof PsiPrefixExpression prefixExpression)) {
      return null;
    }
    final IElementType tokenType = prefixExpression.getOperationTokenType();
    if (!EXCL.equals(tokenType)) {
      return null;
    }
    final PsiExpression operand = prefixExpression.getOperand();
    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(operand);
    return stripped == null ? operand : stripped;
  }

  public static @NotNull String getNegatedExpressionText(@Nullable PsiExpression condition) {
    return getNegatedExpressionText(condition, new CommentTracker());
  }

  public static @NotNull String getNegatedExpressionText(@Nullable PsiExpression condition, CommentTracker tracker) {
    return getNegatedExpressionText(condition, ParenthesesUtils.NUM_PRECEDENCES, tracker);
  }

  /**
   * Returns the number of logical operands in the expression.
   *
   * @param condition The expression
   * @return the number of logical operands in the expression
   */
  public static int getLogicalOperandCount(@Nullable PsiExpression condition) {
    PsiExpression unparenthesizedExpression = PsiUtil.skipParenthesizedExprDown(condition);
    if (!(unparenthesizedExpression instanceof PsiPolyadicExpression infixExpression)) {
      return 1;
    }
    if (!ANDAND.equals(infixExpression.getOperationTokenType())
        && !OROR.equals(infixExpression.getOperationTokenType())
        && (PsiTypes.booleanType().equals(infixExpression.getOperands()[0].getType())
            || PsiTypes.booleanType().equals(PsiPrimitiveType.getUnboxedType(infixExpression.getOperands()[0].getType()))
            || !Arrays.asList(AND, OR).contains(infixExpression.getOperationTokenType()))) {
      return 1;
    }
    int nbOperands = 0;
    for (PsiExpression operand : infixExpression.getOperands()) {
      nbOperands += getLogicalOperandCount(operand);
    }
    return nbOperands;
  }

  private static final CallMatcher STREAM_ANY_MATCH = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "anyMatch");
  private static final CallMatcher STREAM_NONE_MATCH = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "noneMatch");

  private static final CallMatcher OPTIONAL_IS_PRESENT =
    CallMatcher.anyOf(
      CallMatcher.exactInstanceCall(JAVA_UTIL_OPTIONAL, "isPresent").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_INT, "isPresent").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_LONG, "isPresent").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_DOUBLE, "isPresent").parameterCount(0)
    );
  private static final CallMatcher OPTIONAL_IS_EMPTY =
    CallMatcher.anyOf(
      CallMatcher.exactInstanceCall(JAVA_UTIL_OPTIONAL, "isEmpty").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_INT, "isEmpty").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_LONG, "isEmpty").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_DOUBLE, "isEmpty").parameterCount(0)
    );

  private static final class PredicatedReplacement {
    Predicate<? super PsiMethodCallExpression> predicate;
    String name;

    private PredicatedReplacement(@NonNls String name, Predicate<? super PsiMethodCallExpression> predicate) {
      this.predicate = predicate;
      this.name = name;
    }
  }

  private static final List<PredicatedReplacement> ourReplacements = List.of(
    new PredicatedReplacement("isPresent", OPTIONAL_IS_EMPTY),
    new PredicatedReplacement("isEmpty", OPTIONAL_IS_PRESENT.withLanguageLevelAtLeast(LanguageLevel.JDK_11)),
    new PredicatedReplacement("nonNull", CallMatcher.staticCall(JAVA_UTIL_OBJECTS, "isNull")),
    new PredicatedReplacement("isNull", CallMatcher.staticCall(JAVA_UTIL_OBJECTS, "nonNull")),
    new PredicatedReplacement("noneMatch", STREAM_ANY_MATCH),
    new PredicatedReplacement("anyMatch", STREAM_NONE_MATCH)
  );

  private static String findSmartMethodNegation(PsiExpression expression) {
    if (!(expression instanceof PsiMethodCallExpression call)) return null;
    PsiMethodCallExpression copy = (PsiMethodCallExpression)call.copy();
    for (PredicatedReplacement predicatedReplacement : ourReplacements) {
      if (predicatedReplacement.predicate.test(call)) {
        ExpressionUtils.bindCallTo(copy, predicatedReplacement.name);
        return copy.getText();
      }
    }
    return null;
  }

  public static @NotNull String getNegatedExpressionText(@Nullable PsiExpression expression,
                                                         int precedence,
                                                         CommentTracker tracker) {
    if (expression == null) {
      return "";
    }
    if (expression instanceof PsiMethodCallExpression) {
      String smartNegation = findSmartMethodNegation(expression);
      if (smartNegation != null) return smartNegation;
    }
    if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      PsiExpression operand = parenthesizedExpression.getExpression();
      if (operand != null) {
        return '(' + getNegatedExpressionText(operand, tracker) + ')';
      }
    }
    if (expression instanceof PsiAssignmentExpression && expression.getParent() instanceof PsiExpressionStatement) {
      String newOp = null;
      IElementType tokenType = ((PsiAssignmentExpression)expression).getOperationTokenType();
      if (tokenType == ANDEQ) {
        newOp = "|=";
      }
      else if (tokenType == OREQ) {
        newOp = "&=";
      }
      if (newOp != null) {
        return tracker.text(((PsiAssignmentExpression)expression).getLExpression()) +
               newOp +
               getNegatedExpressionText(((PsiAssignmentExpression)expression).getRExpression());
      }
    }
    if (expression instanceof PsiConditionalExpression conditionalExpression) {
      final boolean needParenthesis = ParenthesesUtils.getPrecedence(conditionalExpression) >= precedence;
      final String text = tracker.text(conditionalExpression.getCondition()) +
                          '?' + getNegatedExpressionText(conditionalExpression.getThenExpression(), tracker) +
                          ':' + getNegatedExpressionText(conditionalExpression.getElseExpression(), tracker);
      return needParenthesis ? "(" + text + ")" : text;
    }
    if (isNegation(expression)) {
      final PsiExpression negated = getNegated(expression);
      if (negated != null) {
        return ParenthesesUtils.getText(tracker.markUnchanged(negated), precedence);
      }
    }
    if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (ComparisonUtils.isComparison(polyadicExpression)) {
        final String negatedComparison = ComparisonUtils.getNegatedComparison(tokenType);
        final StringBuilder result = new StringBuilder();
        final boolean isEven = (operands.length & 1) != 1;
        for (int i = 0, length = operands.length; i < length; i++) {
          final PsiExpression operand = operands[i];
          if (TypeUtils.hasFloatingPointType(operand) && !ComparisonUtils.isEqualityComparison(polyadicExpression)) {
            // preserve semantics for NaNs
            return "!(" + polyadicExpression.getText() + ')';
          }
          if (i > 0) {
            if (isEven && (i & 1) != 1) {
              final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(operand);
              if (token != null) {
                result.append(token.getText());
              }
            }
            else {
              result.append(negatedComparison);
            }
          }
          result.append(tracker.text(operand));
        }
        return result.toString();
      }
      if (tokenType.equals(ANDAND) || tokenType.equals(OROR)) {
        final String targetToken;
        final int newPrecedence;
        if (tokenType.equals(ANDAND)) {
          targetToken = "||";
          newPrecedence = ParenthesesUtils.OR_PRECEDENCE;
        }
        else {
          targetToken = "&&";
          newPrecedence = ParenthesesUtils.AND_PRECEDENCE;
        }
        final Function<PsiElement, String> replacer = child -> {
          if (child instanceof PsiExpression) {
            return getNegatedExpressionText((PsiExpression)child, newPrecedence, tracker);
          }
          return child instanceof PsiJavaToken ? targetToken : tracker.text(child);
        };
        final String join = StringUtil.join(polyadicExpression.getChildren(), replacer, "");
        return (newPrecedence > precedence) ? '(' + join + ')' : join;
      }
    }
    if (expression instanceof PsiLiteralExpression) {
      Object value = ((PsiLiteralExpression)expression).getValue();
      if (value instanceof Boolean) {
        return String.valueOf(!((Boolean)value));
      }
    }
    return '!' + ParenthesesUtils.getText(tracker.markUnchanged(expression), ParenthesesUtils.PREFIX_PRECEDENCE);
  }

  public static @Nullable PsiExpression findNegation(PsiExpression expression) {
    PsiExpression ancestor = expression;
    PsiElement parent = ancestor.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      ancestor = (PsiExpression)parent;
      parent = ancestor.getParent();
    }
    if (parent instanceof PsiPrefixExpression prefixAncestor) {
      if (EXCL.equals(prefixAncestor.getOperationTokenType())) {
        return prefixAncestor;
      }
    }
    return null;
  }

  @Contract("null -> false")
  public static boolean isBooleanLiteral(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof PsiLiteralExpression literalExpression)) {
      return false;
    }
    final @NonNls String text = literalExpression.getText();
    return JavaKeywords.TRUE.equals(text) || JavaKeywords.FALSE.equals(text);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isTrue(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) {
      return false;
    }
    return JavaKeywords.TRUE.equals(expression.getText());
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isFalse(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) {
      return false;
    }
    return JavaKeywords.FALSE.equals(expression.getText());
  }

  /**
   * Checks whether two supplied boolean expressions are opposite to each other (e.g. "a == null" and "a != null")
   *
   * @param expression1 first expression
   * @param expression2 second expression
   * @return true if it's determined that the expressions are opposite to each other.
   */
  @Contract(value = "null, _ -> false; _, null -> false", pure = true)
  public static boolean areExpressionsOpposite(@Nullable PsiExpression expression1, @Nullable PsiExpression expression2) {
    expression1 = PsiUtil.skipParenthesizedExprDown(expression1);
    expression2 = PsiUtil.skipParenthesizedExprDown(expression2);
    if (expression1 == null || expression2 == null) return false;
    EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
    if (isNegation(expression1)) {
      return equivalence.expressionsAreEquivalent(getNegated(expression1), expression2);
    }
    if (isNegation(expression2)) {
      return equivalence.expressionsAreEquivalent(getNegated(expression2), expression1);
    }
    if (expression1 instanceof PsiBinaryExpression binOp1 && expression2 instanceof PsiBinaryExpression binOp2) {
      RelationType rel1 = DfaPsiUtil.getRelationByToken(binOp1.getOperationTokenType());
      RelationType rel2 = DfaPsiUtil.getRelationByToken(binOp2.getOperationTokenType());
      if (rel1 == null || rel2 == null) return false;
      PsiType type = binOp1.getLOperand().getType();
      // a > b and a <= b are not strictly opposite due to NaN semantics
      if (type == null || type.equals(PsiTypes.floatType()) || type.equals(PsiTypes.doubleType())) return false;
      if (rel1 == rel2.getNegated()) {
        return equivalence.expressionsAreEquivalent(binOp1.getLOperand(), binOp2.getLOperand()) &&
               equivalence.expressionsAreEquivalent(binOp1.getROperand(), binOp2.getROperand());
      }
      if (rel1.getFlipped() == rel2.getNegated()) {
        return equivalence.expressionsAreEquivalent(binOp1.getLOperand(), binOp2.getROperand()) &&
               equivalence.expressionsAreEquivalent(binOp1.getROperand(), binOp2.getLOperand());
      }
    }
    return false;
  }

  /**
   * Evaluates a given {@link PsiExpression} to determine if it references a constant field
   * within the {@code java.lang.Boolean} class, specifically {@link Boolean#TRUE} or {@link Boolean#FALSE}.
   *
   * @param expression the {@code PsiExpression} to be evaluated.
   * @return {@code Boolean.TRUE} if {@code expression} references the {@code Boolean.TRUE} constant,
   * {@code Boolean.FALSE} if it references the {@code Boolean.FALSE} constant,
   * or {@code null} if it does not reference a recognized {@code java.lang.Boolean} constant field.
   */
  public static @Nullable Boolean fromBoxedConstantReference(@NotNull PsiExpression expression) {
    PsiExpression unparenthesizedExpression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(unparenthesizedExpression instanceof PsiReferenceExpression referenceExpression)) return null;
    if (!(referenceExpression.resolve() instanceof PsiField field)) return null;
    final PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return null;
    if (CommonClassNames.JAVA_LANG_BOOLEAN.equals(field.getContainingClass().getQualifiedName())) {
      return switch (field.getName()) {
        case "TRUE" -> true;
        case "FALSE" -> false;
        default -> null;
      };
    }
    return null;
  }
}
