/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ipp.psiutils.ErrorUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.*;

import static com.intellij.java.codeserver.core.JavaPatternExhaustivenessUtil.hasExhaustivenessError;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

public final class SwitchUtils {

  public static final CallMatcher STRING_IS_EMPTY = instanceCall(JAVA_LANG_STRING, "isEmpty").parameterCount(0);

  private SwitchUtils() {}

  /**
   * Calculates the number of branches in the specified switch statement.
   * When a default case is present the count will be returned as a negative number,
   * e.g. if a switch statement contains 4 labeled cases and a default case, it will return -5
   * @param statement  the statement to count the cases of.
   * @return a negative number if a default case was encountered.
   */
  public static int calculateBranchCount(@NotNull PsiSwitchStatement statement) {
    // preserved for plugin compatibility
    return calculateBranchCount((PsiSwitchBlock)statement);
  }

  /**
   * Calculates the number of branches in the specified switch block.
   * When a default case is present the count will be returned as a negative number,
   * e.g. if a switch block contains 4 labeled cases and a default case, it will return -5
   *
   * @param block the switch block to count the cases of.
   * @return a negative number if a default case was encountered.
   */
  public static int calculateBranchCount(@NotNull PsiSwitchBlock block) {
    List<PsiElement> switchBranches = JavaPsiSwitchUtil.getSwitchBranches(block);
    if (switchBranches.isEmpty()) return 0;
    int branches = 0;
    boolean defaultFound = false;
    for (PsiElement branch : switchBranches) {
      if (branch instanceof PsiSwitchLabelStatementBase) {
        if (((PsiSwitchLabelStatementBase)branch).isDefaultCase()) {
          defaultFound = true;
        }
      }
      else if (branch instanceof PsiCaseLabelElement) {
        if (branch instanceof PsiDefaultCaseLabelElement) {
          defaultFound = true;
        }
        else {
          branches++;
        }
      }
    }
    final PsiCodeBlock body = block.getBody();
    if (body == null) {
      return 0;
    }
    return defaultFound ? -branches - 1 : branches;
  }

  /**
   * Counts the number of unconditional patterns applicable to the provided selector type.
   *
   * @param selector the PSI expression for which unconditionally applicable patterns are to be counted
   * @param existingCaseValues the set of existing case values to check against
   * @return the number of unconditional patterns applicable to the selector type
   */
  public static int countUnconditionalPatterns(@NotNull PsiExpression selector,
                                               @NotNull Set<Object> existingCaseValues) {
    PsiType selectorType = selector.getType();
    if (selectorType == null) return 0;
    int count = 0;
    for (Object caseValue : existingCaseValues) {
      if (caseValue instanceof PsiPattern && JavaPsiPatternUtil.isUnconditionalForType((PsiPattern)caseValue, selectorType)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Determines whether a given expression can be used as a case in a switch statement,
   * considering the provided language level, existing case values, and whether pattern matching is used.
   * It doesn't check if this expression has several unconditional patterns.
   * See {@link SwitchUtils#countUnconditionalPatterns(PsiExpression, Set)}
   *
   * @param expression         the expression to be checked
   * @param switchExpression   the selector used in the switch statement
   * @param languageLevel      the language level at which the switch statement is being compiled
   * @param existingCaseValues a set of existing case values to ensure no duplicates
   * @param isPatternMatch     flag indicating if pattern matching is being used
   * @return true if the expression can be used as a case in the switch statement; false otherwise
   */
  public static boolean canBeSwitchCase(@Nullable PsiExpression expression,
                                        @NotNull PsiExpression switchExpression,
                                        @NotNull LanguageLevel languageLevel,
                                        @NotNull Set<Object> existingCaseValues,
                                        boolean isPatternMatch) {
    if (expression == null) return false;
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    boolean primitiveTypesInPatternsSufficient = JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.isSufficient(languageLevel);
    if (isPatternMatch || primitiveTypesInPatternsSufficient) {
      boolean patternSwitchCase = canBePatternSwitchCase(expression, switchExpression, existingCaseValues);
      if (patternSwitchCase) return true;
      if (!primitiveTypesInPatternsSufficient) return false;
    }
    final EqualityCheck check = EqualityCheck.from(expression);
    if (check != null) {
      final PsiExpression left = check.getLeft();
      final PsiExpression right = check.getRight();
      if (canBeCaseLabel(left, languageLevel, existingCaseValues)) {
        return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, right);
      }
      else if (canBeCaseLabel(right, languageLevel, existingCaseValues)) {
        return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, left);
      }
    }
    if (primitiveTypesInPatternsSufficient && isComparisonWithPrimitives(expression, switchExpression, existingCaseValues)) {
      return true;
    }

    if (expression instanceof PsiMethodCallExpression methodCallExpression && STRING_IS_EMPTY.test(methodCallExpression) &&
        EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(
          (methodCallExpression).getMethodExpression().getQualifierExpression(), switchExpression)) {
      return existingCaseValues.add("");
    }

    if (!(expression instanceof PsiPolyadicExpression polyadicExpression)) {
      return false;
    }
    final IElementType operation = polyadicExpression.getOperationTokenType();
    final PsiExpression[] operands = polyadicExpression.getOperands();
    if (operation.equals(JavaTokenType.OROR)) {
      for (PsiExpression operand : operands) {
        if (!canBeSwitchCase(operand, switchExpression, languageLevel, existingCaseValues, false)) {
          return false;
        }
      }
      return true;
    }
    else if (operation.equals(JavaTokenType.EQEQ) && operands.length == 2) {
      return (canBeCaseLabel(operands[0], languageLevel, existingCaseValues) &&
              !isExtendedSwitchSelectorType(operands[1].getType()) &&
              EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, operands[1])) ||
             (canBeCaseLabel(operands[1], languageLevel, existingCaseValues) &&
              !isExtendedSwitchSelectorType(operands[0].getType()) &&
              EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, operands[0]));
    }
    else {
      return false;
    }
  }

  /**
   * Checks whether the given primitive or boxed type is an extended switch with primitive types.
   *
   * @param primitiveOrBoxedType the primitive type to check
   * @return true if the primitive type is an extended primitive type, otherwise false
   */
  public static boolean isExtendedSwitchSelectorType(@Nullable PsiType primitiveOrBoxedType) {
    PsiPrimitiveType primitiveType = PsiPrimitiveType.getOptionallyUnboxedType(primitiveOrBoxedType);
    if (primitiveType == null) return false;
    return PsiTypes.booleanType().equals(primitiveType) ||
           PsiTypes.longType().equals(primitiveType) ||
           PsiTypes.doubleType().equals(primitiveType) ||
           PsiTypes.floatType().equals(primitiveType);
  }

  /**
   * Checks if the given expression is a comparison with primitives or wrappers for extended switch with primitive types
   *
   * @param psiExpression        the expression to check
   * @param switchExpression     the switch expression to compare with
   * @param existingCaseValues   a set of existing case values
   * @return true if the expression is a comparison with primitives, false otherwise
   */
  private static boolean isComparisonWithPrimitives(@Nullable PsiExpression psiExpression, @NotNull PsiExpression switchExpression,
                                                    @NotNull Set<Object> existingCaseValues) {
    if (!(psiExpression instanceof PsiBinaryExpression psiBinaryExpression)) {
      return false;
    }
    if (!(TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(switchExpression.getType()))) {
      return false;
    }
    if (psiBinaryExpression.getOperationTokenType() != JavaTokenType.EQEQ) {
      return false;
    }
    boolean isEqual = psiBinaryExpression.getOperationTokenType() == JavaTokenType.EQEQ;
    final PsiExpression left = psiBinaryExpression.getLOperand();
    final PsiExpression right = psiBinaryExpression.getROperand();
    if (right == null) return false;
    if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, right) &&
        primitiveValueCanBeUsedForComparisonInCase(left, right, isEqual ? existingCaseValues : null)) {
      return true;
    }
    else {
      if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, left) &&
          primitiveValueCanBeUsedForComparisonInCase(right, left, isEqual ? existingCaseValues : null)) {
        return true;
      }
    }
    return false;
  }

  private static boolean primitiveValueCanBeUsedForComparisonInCase(@Nullable PsiExpression value,
                                                                    @Nullable PsiExpression selector,
                                                                    @Nullable Set<Object> existingCaseValues) {
    if (value == null || selector == null) return false;
    Object o = ExpressionUtils.computeConstantExpression(value);
    if (o == null) {
      return false;
    }
    if (existingCaseValues != null && existingCaseValues.contains(o)) {
      return false;
    }
    if (existingCaseValues != null) {
      existingCaseValues.add(o);
    }
    PsiType selectorType = selector.getType();
    PsiType valueType = value.getType();
    if (selectorType == null || valueType == null) return false;
    PsiPrimitiveType unwrapped = PsiPrimitiveType.getOptionallyUnboxedType(selectorType);
    if (unwrapped != null && isExtendedSwitchSelectorType(unwrapped)) {
      return unwrapped.equals(valueType);
    }
    return TypeConversionUtil.isAssignable(selectorType, valueType);
  }

  private static boolean canBePatternSwitchCase(@Nullable PsiExpression expression,
                                                @NotNull PsiExpression switchExpression,
                                                @NotNull Set<Object> existingCaseValues) {
    if (!canBePatternSwitchCase(expression, switchExpression)) {
      return false;
    }
    if (isNullComparison(expression, switchExpression)) {
      return existingCaseValues.add(null);
    }
    final PsiCaseLabelElement pattern = createPatternFromExpression(expression);
    if (pattern == null) {
      if (expression instanceof PsiPolyadicExpression polyadicExpression &&
          polyadicExpression.getOperationTokenType().equals(JavaTokenType.OROR)) {
        for (@NotNull PsiElement child : polyadicExpression.getOperands()) {
          if (!(child instanceof PsiExpression childExpression)) {
            return false;
          }
          if (!canBePatternSwitchCase(childExpression, switchExpression, existingCaseValues)) {
            return false;
          }
          return true;
        }
      }
      return false;
    }
    if (VariableAccessUtils.isAnyVariableAssigned(VariableAccessUtils.collectUsedVariables(expression), expression)) {
      return false;
    }
    if (!PsiTreeUtil.findChildrenOfType(expression, PsiDeclarationStatement.class).isEmpty()) {
      return false;
    }
    for (Object caseValue : existingCaseValues) {
      if (caseValue instanceof PsiPattern && JavaPsiPatternUtil.dominates((PsiPattern)caseValue, pattern)) {
        return false;
      }
    }
    existingCaseValues.add(pattern);
    return true;
  }

  private static boolean isNullComparison(@Nullable PsiExpression expression, @NotNull PsiExpression switchExpression) {
    if (expression == null) return false;
    PsiExpression operand = findNullCheckedOperand(expression);
    return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, operand);
  }

  public static @Nullable PsiCaseLabelElement createPatternFromExpression(@NotNull PsiExpression expression) {
    final PsiElementFactory factory = PsiElementFactory.getInstance(expression.getProject());
    final String patternCaseText = createPatternCaseText(expression);
    if (patternCaseText == null) return null;
    final String labelText = "case " + patternCaseText + "->{}";
    final PsiStatement statement = factory.createStatementFromText(labelText, null);
    final PsiSwitchLabelStatementBase label = ObjectUtils.tryCast(statement, PsiSwitchLabelStatementBase.class);
    assert label != null;
    return Objects.requireNonNull(label.getCaseLabelElementList()).getElements()[0];
  }

  /**
   * Returns true if given switch block has a rule-based format (like 'case 0 ->')
   * @param block a switch block to test
   * @return true if given switch block has a rule-based format; false if it has conventional label-based format (like 'case 0:')
   * If switch body has no labels yet and language level permits the rule-based format is assumed.
   */
  @Contract(pure = true)
  public static boolean isRuleFormatSwitch(@NotNull PsiSwitchBlock block) {
    return PsiUtil.isRuleFormatSwitch(block);
  }

  public static boolean canBeSwitchSelectorExpression(PsiExpression expression, LanguageLevel languageLevel) {
    if (expression == null) {
      return false;
    }
    final PsiType type = expression.getType();
    if (PsiTypes.charType().equals(type) || PsiTypes.byteType().equals(type) || PsiTypes.shortType().equals(type) || PsiTypes.intType()
      .equals(type)) {
      return true;
    }
    else if (type instanceof PsiClassType && languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
      if (type.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER) || type.equalsToText(CommonClassNames.JAVA_LANG_BYTE) ||
          type.equalsToText(CommonClassNames.JAVA_LANG_SHORT) || type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER)) {
        return true;
      }
      if (TypeConversionUtil.isEnumType(type)) {
        return true;
      }
      if (JavaFeature.STRING_SWITCH.isSufficient(languageLevel) && type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return true;
      }
      return PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, expression);
    }
    if (JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.isSufficient(languageLevel) && type instanceof PsiPrimitiveType) {
      return true;
    }
    return false;
  }

  @Contract("null -> null")
  public static @Nullable PsiExpression getSwitchSelectorExpression(PsiExpression expression) {
    if (expression == null) return null;
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expression);
    final PsiExpression selectorExpression = getPossibleSwitchSelectorExpression(expression, languageLevel);
    return canBeSwitchSelectorExpression(selectorExpression, languageLevel) ? selectorExpression : null;
  }

  private static PsiExpression getPossibleSwitchSelectorExpression(PsiExpression expression, LanguageLevel languageLevel) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) {
      return null;
    }
    final EqualityCheck check = EqualityCheck.from(expression);
    if (check != null) {
      final PsiExpression left = check.getLeft();
      final PsiExpression right = check.getRight();
      if (canBeCaseLabel(left, languageLevel, null)) {
        return right;
      }
      else if (canBeCaseLabel(right, languageLevel, null)) {
        return left;
      }
    }
    if (PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, expression)) {
      final PsiExpression patternSwitchExpression = findPatternSwitchExpression(expression);
      if (patternSwitchExpression != null) return patternSwitchExpression;
    }
    if (expression instanceof PsiMethodCallExpression methodCallExpression &&
        STRING_IS_EMPTY.test(methodCallExpression)) {
      return methodCallExpression.getMethodExpression().getQualifierExpression();
    }
    if (!(expression instanceof PsiPolyadicExpression polyadicExpression)) {
      return null;
    }
    final IElementType operation = polyadicExpression.getOperationTokenType();
    final PsiExpression[] operands = polyadicExpression.getOperands();
    if (operation.equals(JavaTokenType.OROR) && operands.length > 0) {
      return getPossibleSwitchSelectorExpression(operands[0], languageLevel);
    }
    else if (operation.equals(JavaTokenType.EQEQ) && operands.length == 2) {
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(operands[0]);
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(operands[1]);
      if (canBeCaseLabel(lhs, languageLevel, null)) {
        return rhs;
      }
      else if (canBeCaseLabel(rhs, languageLevel, null)) {
        return lhs;
      }
    }
    if (JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.isSufficient(languageLevel) &&
        expression instanceof PsiBinaryExpression psiBinaryExpression) {
      IElementType operationTokenType = psiBinaryExpression.getOperationTokenType();
      if (!operationTokenType.equals(JavaTokenType.EQEQ) &&
          !operationTokenType.equals(JavaTokenType.GT) &&
          !operationTokenType.equals(JavaTokenType.GE) &&
          !operationTokenType.equals(JavaTokenType.LT) &&
          !operationTokenType.equals(JavaTokenType.LE)) {
        return null;
      }
      final PsiExpression left = psiBinaryExpression.getLOperand();
      final PsiExpression right = psiBinaryExpression.getROperand();
      if (primitiveValueCanBeUsedForComparisonInCase(left, right, null)) {
        return right;
      }
      else if (primitiveValueCanBeUsedForComparisonInCase(right, left, null)) {
        return left;
      }
    }
    return null;
  }

  private static @Nullable PsiExpression findPossiblePatternOperand(@Nullable PsiExpression expression) {
    if (expression instanceof PsiInstanceOfExpression psiInstanceOfExpression) {
      if (isUsedOutsideParentIf(psiInstanceOfExpression)) {
        return null;
      }
      return psiInstanceOfExpression.getOperand();
    }
    if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      final IElementType operationToken = polyadicExpression.getOperationTokenType();
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (JavaTokenType.ANDAND.equals(operationToken)) {
        final PsiExpression patternOperand = findPossiblePatternOperand(operands[0]);
        if (patternOperand != null) return patternOperand;
        for (PsiExpression operand : operands) {
          final PsiExpression pattern = findPossiblePatternOperand(operand);
          if (pattern != null) return pattern;
          if (SideEffectChecker.mayHaveSideEffects(operand)) break;
        }
      }
    }
    return null;
  }

  private static boolean isUsedOutsideParentIf(@NotNull PsiInstanceOfExpression expression) {
    PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class);
    if (!PsiTreeUtil.isAncestor(ifStatement, expression, false)) {
      //something strange, return true as safe result
      return true;
    }
    return JavaPsiPatternUtil.getExposedPatternVariables(expression)
      .stream().flatMap(variable -> VariableAccessUtils.getVariableReferences(variable, ifStatement.getParent()).stream())
      .anyMatch(variable -> !PsiTreeUtil.isAncestor(ifStatement, variable, false));
  }

  public static @Nullable PsiExpression findPatternSwitchExpression(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    final PsiExpression patternOperand = findPossiblePatternOperand(expression);
    if (patternOperand != null) return patternOperand;
    final PsiExpression nullCheckedOperand = findNullCheckedOperand(expression);
    if (nullCheckedOperand != null) return nullCheckedOperand;
    if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      final IElementType operationToken = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.OROR.equals(operationToken)) {
        final PsiExpression[] operands = polyadicExpression.getOperands();
        if (operands.length == 2) {
          PsiExpression firstOperand = findNullCheckedOperand(operands[0]);
          PsiExpression secondOperand = findPossiblePatternOperand(operands[1]);
          if (firstOperand == null || secondOperand == null) {
            firstOperand = findPossiblePatternOperand(operands[0]);
            secondOperand = findNullCheckedOperand(operands[1]);
          }
          if (firstOperand == null || secondOperand == null) {
            return null;
          }
          if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(firstOperand, secondOperand)) {
            return firstOperand;
          }
        }
      }
    }
    return null;
  }

  @Contract("null, _ -> false")
  public static boolean canBePatternSwitchCase(@Nullable PsiExpression expression, @NotNull PsiExpression switchExpression) {
    if (expression == null) return false;
    PsiExpression localSwitchExpression = findPatternSwitchExpression(expression);
    if (localSwitchExpression == null && expression instanceof PsiPolyadicExpression polyadicExpression) {
      localSwitchExpression = findSelectorWithComparedPrimitives(polyadicExpression);
    }
    if (localSwitchExpression == null) return false;
    return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(localSwitchExpression, switchExpression);
  }

  public static @Nullable PsiExpression findNullCheckedOperand(PsiExpression expression) {
    if (!(expression instanceof PsiBinaryExpression binaryExpression)) return null;
    if (!JavaTokenType.EQEQ.equals(binaryExpression.getOperationTokenType())) return null;
    if (ExpressionUtils.isNullLiteral(binaryExpression.getLOperand())) {
      return binaryExpression.getROperand();
    }
    else if (ExpressionUtils.isNullLiteral(binaryExpression.getROperand())) {
      return binaryExpression.getLOperand();
    }
    else {
      return null;
    }
  }

  public static @Nullable @NonNls String createPatternCaseText(PsiExpression expression){
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiInstanceOfExpression instanceOf) {
      final PsiPrimaryPattern pattern = instanceOf.getPattern();
      if (pattern != null) {
        if (pattern instanceof PsiDeconstructionPattern deconstruction && ErrorUtil.containsError(deconstruction.getDeconstructionList())) {
          return null;
        }
        return pattern.getText();
      }
      final PsiTypeElement typeElement = instanceOf.getCheckType();
      final PsiType type = typeElement != null ? typeElement.getType() : null;
      String name = new VariableNameGenerator(instanceOf, VariableKind.LOCAL_VARIABLE).byType(type).generate(true);
      String typeText = typeElement != null ? typeElement.getText() : CommonClassNames.JAVA_LANG_OBJECT;
      return typeText + " " + name;
    }
    if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      String textWithPrimitives = getSwitchCaseTextWithComparedPrimitives(polyadicExpression);
      if (textWithPrimitives != null) return textWithPrimitives;
      final IElementType operationToken = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.ANDAND.equals(operationToken)) {
        final PsiExpression[] operands = polyadicExpression.getOperands();
        final PsiExpression instanceOf = ContainerUtil.find(operands, operand -> operand instanceof PsiInstanceOfExpression);
        StringBuilder builder = new StringBuilder();
        builder.append(createPatternCaseText(instanceOf));
        boolean needAppendWhen = PsiUtil.isAvailable(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS, expression);
        if (!needAppendWhen) return null; // impossible to support old style guarded with '&&'
        for (PsiExpression operand : operands) {
          if (operand != instanceOf) {
            builder.append(needAppendWhen ? " when " : " && ").append(operand.getText());
            needAppendWhen = false;
          }
        }
        return builder.toString();
      }
    }
    return null;
  }

  /**
   * Returns the switch case text with compared primitives.
   * Example:
   * i > 1 -> int i2 when i2 > 1
   * i>1 && i<1 -> int i2 when i2 > 1 && i2 < 1
   *
   * @param expression the PsiPolyadicExpression representing the expression to analyze
   * @return the switch case text with compared primitives, or null if it cannot be generated
   */
  private static @Nullable String getSwitchCaseTextWithComparedPrimitives(@NotNull PsiPolyadicExpression expression) {
    PsiExpression switchSelector = findSelectorWithComparedPrimitives(expression);
    if (switchSelector == null) return null;
    PsiType switchSelectorType = switchSelector.getType();
    if (switchSelectorType == null) return null;

    StringBuilder stringBuilder = new StringBuilder();
    String name = new VariableNameGenerator(switchSelector, VariableKind.LOCAL_VARIABLE).byType(
      switchSelectorType).generate(true);
    String typeText = switchSelectorType.getPresentableText();
    stringBuilder.append(typeText).append(" ").append(name).append(" when ");
    for (PsiElement child : expression.getChildren()) {
      if (child instanceof PsiBinaryExpression childBinaryExpression) {
        for (PsiElement binaryChild : childBinaryExpression.getChildren()) {
          if (isSwitchSelector(binaryChild, switchSelector)) {
            stringBuilder.append(name);
          }
          else {
            stringBuilder.append(binaryChild.getText());
          }
        }
      }
      else {
        if (isSwitchSelector(child, switchSelector)) {
          stringBuilder.append(name);
        }
        else {
          stringBuilder.append(child.getText());
        }
      }
    }
    return stringBuilder.toString();
  }

  private static boolean isSwitchSelector(@NotNull PsiElement child, @NotNull PsiExpression selector) {
    if (child == selector) return true;
    if (!child.getText().equals(selector.getText())) return false;
    return child instanceof PsiReferenceExpression childReference &&
           selector instanceof PsiReferenceExpression selectorReference &&
           childReference.resolve() instanceof PsiVariable childVariable &&
           selectorReference.resolve() instanceof PsiVariable selectorVariable &&
           child.getManager().areElementsEquivalent(childVariable, selectorVariable);
  }

  private static @Nullable PsiExpression findSelectorWithComparedPrimitives(@NotNull PsiPolyadicExpression expression) {
    if (!PsiUtil.isAvailable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, expression)) {
      return null;
    }
    IElementType operationTokenType = expression.getOperationTokenType();
    List<PsiBinaryExpression> binaryExpressions = new ArrayList<>();
    if (operationTokenType == JavaTokenType.OROR || operationTokenType == JavaTokenType.ANDAND) {
      for (PsiExpression operand : expression.getOperands()) {
        if (operand instanceof PsiBinaryExpression psiBinaryExpression) {
          binaryExpressions.add(psiBinaryExpression);
        }
        else {
          return null;
        }
      }
    }
    else if (expression instanceof PsiBinaryExpression binaryExpression) {
      binaryExpressions.add(binaryExpression);
    }
    else {
      return null;
    }
    PsiExpression switchSelector = null;
    for (PsiBinaryExpression binaryExpression : binaryExpressions) {
      IElementType binaryExpressionOperationTokenType = binaryExpression.getOperationTokenType();
      if (JavaTokenType.LE.equals(binaryExpressionOperationTokenType) ||
          JavaTokenType.LT.equals(binaryExpressionOperationTokenType) ||
          JavaTokenType.GE.equals(binaryExpressionOperationTokenType) ||
          JavaTokenType.GT.equals(binaryExpressionOperationTokenType)) {
        PsiExpression lOperand = binaryExpression.getLOperand();
        PsiExpression rOperand = binaryExpression.getROperand();
        EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
        if (lOperand instanceof PsiReferenceExpression referenceExpression && referenceExpression.resolve() instanceof PsiVariable &&
            ExpressionUtils.computeConstantExpression(rOperand) != null) {
          if (switchSelector != null && !equivalence.expressionsAreEquivalent(switchSelector, lOperand)) {
            return null;
          }
          switchSelector = lOperand;
        }
        else if (rOperand instanceof PsiReferenceExpression referenceExpression && referenceExpression.resolve() instanceof PsiVariable &&
                 ExpressionUtils.computeConstantExpression(lOperand) != null) {
          if (switchSelector != null && !equivalence.expressionsAreEquivalent(switchSelector, rOperand)) {
            return null;
          }
          switchSelector = rOperand;
        }
      }
    }
    if (switchSelector == null) {
      return null;
    }
    PsiType switchSelectorType = switchSelector.getType();
    if (switchSelectorType == null || !TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(switchSelectorType)) {
      return null;
    }
    return switchSelector;
  }

  private static boolean canBeCaseLabel(@Nullable PsiExpression expression,
                                        @NotNull LanguageLevel languageLevel,
                                        @Nullable Set<Object> existingCaseValues) {
    if (expression == null) {
      return false;
    }
    if (JavaFeature.ENUMS.isSufficient(languageLevel) && expression instanceof PsiReferenceExpression ref) {
      final PsiElement referent = ref.resolve();
      if (referent instanceof PsiEnumConstant) {
        return existingCaseValues == null || existingCaseValues.add(referent);
      }
    }
    final PsiType type = expression.getType();
    if ((!JavaFeature.STRING_SWITCH.isSufficient(languageLevel) || !TypeUtils.isJavaLangString(type)) &&
        !PsiTypes.intType().equals(type) &&
        !PsiTypes.shortType().equals(type) &&
        !PsiTypes.byteType().equals(type) &&
        !PsiTypes.charType().equals(type)) {
      return false;
    }
    final Object value = ExpressionUtils.computeConstantExpression(expression);
    if (value == null) {
      return false;
    }
    return existingCaseValues == null || existingCaseValues.add(value);
  }

  public static String findUniqueLabelName(PsiStatement statement, @NonNls String baseName) {
    final PsiElement ancestor = PsiTreeUtil.getParentOfType(statement, PsiMember.class);
    if (ancestor == null || !checkForLabel(baseName, ancestor)) {
      return baseName;
    }
    int val = 1;
    while (true) {
      final String name = baseName + val;
      if (!checkForLabel(name, ancestor)) {
        return name;
      }
      val++;
    }
  }

  private static boolean checkForLabel(String name, PsiElement ancestor) {
    final LabelSearchVisitor visitor = new LabelSearchVisitor(name);
    ancestor.accept(visitor);
    return visitor.isUsed();
  }

  /**
   * @param label a switch label statement
   * @return list of enum constants which are targets of the specified label; empty list if the supplied element is not a switch label,
   * or it is not an enum switch.
   */
  public static @NotNull @Unmodifiable List<PsiEnumConstant> findEnumConstants(PsiSwitchLabelStatementBase label) {
    if (label == null) {
      return Collections.emptyList();
    }
    final PsiCaseLabelElementList list = label.getCaseLabelElementList();
    if (list == null) {
      return Collections.emptyList();
    }
    List<PsiEnumConstant> constants = new ArrayList<>();
    for (PsiCaseLabelElement labelElement : list.getElements()) {
      if (labelElement instanceof PsiDefaultCaseLabelElement ||
          labelElement instanceof PsiExpression expr && ExpressionUtils.isNullLiteral(expr)) {
        continue;
      }
      if (labelElement instanceof PsiReferenceExpression) {
        final PsiElement target = ((PsiReferenceExpression)labelElement).resolve();
        if (target instanceof PsiEnumConstant) {
          constants.add((PsiEnumConstant)target);
          continue;
        }
      }
      return Collections.emptyList();
    }
    return constants;
  }

  /**
   * Checks if the given switch label statement contains a {@code default} case
   *
   * @param label a switch label statement to test
   * @return {@code true} if the given switch label statement contains a {@code default} case, {@code false} otherwise
   */
  public static boolean isDefaultLabel(@Nullable PsiSwitchLabelStatementBase label) {
    if (label == null) return false;
    if (label.isDefaultCase()) return true;
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    if (labelElementList == null) return false;
    return ContainerUtil.exists(labelElementList.getElements(), element -> element instanceof PsiDefaultCaseLabelElement);
  }

  /**
   * Checks if the given switch label statement contains only a {@code default} case and nothing else
   *
   * @param label a switch label statement to test
   * @return {@code true} if the given switch label statement contains only a {@code default} case and nothing else,
   * {@code false} otherwise.
   */
  public static boolean hasOnlyDefaultCase(@Nullable PsiSwitchLabelStatementBase label) {
    if (label == null) return false;
    if (label.isDefaultCase()) return true;
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    return labelElementList != null &&
           labelElementList.getElementCount() == 1 &&
           labelElementList.getElements()[0] instanceof PsiDefaultCaseLabelElement;
  }

  /**
   * Checks if the label has the following form {@code 'case null'}
   *
   * @param label label to check
   * @return {@code true} if the label has the following form {@code 'case null'}, {@code false} otherwise.
   */
  public static boolean isCaseNull(@Nullable PsiSwitchLabelStatementBase label) {
    if (label == null) return false;
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    return labelElementList != null &&
           labelElementList.getElementCount() == 1 &&
           labelElementList.getElements()[0] instanceof PsiExpression expr &&
           ExpressionUtils.isNullLiteral(expr);
  }

  /**
   * Checks if the label has the following form {@code 'case null, default'}
   *
   * @param label label to check
   * @return {@code true} if the label has the following form {@code 'case null, default'}, {@code false} otherwise.
   */
  public static boolean isCaseNullDefault(@Nullable PsiSwitchLabelStatementBase label) {
    if (label == null) return false;
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    return labelElementList != null &&
           labelElementList.getElementCount() == 2 &&
           labelElementList.getElements()[0] instanceof PsiExpression expr &&
           ExpressionUtils.isNullLiteral(expr) &&
           labelElementList.getElements()[1] instanceof PsiDefaultCaseLabelElement;
  }

  /**
   * Checks if the given switch label statement contains a {@code default} case or an unconditional pattern
   *
   * @param label a switch label statement to test
   * @return {@code true} if the given switch label statement contains a {@code default} case or an unconditional pattern,
   * {@code false} otherwise.
   */
  public static boolean isUnconditionalLabel(@Nullable PsiSwitchLabelStatementBase label) {
    if (label == null) return false;
    if (isDefaultLabel(label)) return true;
    PsiSwitchBlock switchBlock = label.getEnclosingSwitchBlock();
    if (switchBlock == null) return false;
    PsiExpression expression = switchBlock.getExpression();
    if (expression == null) return false;
    PsiType type = expression.getType();
    if (type == null) return false;
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    if (labelElementList == null) return false;
    return StreamEx.of(labelElementList.getElements())
      .select(PsiPattern.class)
      .anyMatch(pattern -> JavaPsiPatternUtil.isUnconditionalForType(pattern, type));
  }

  /**
   * Finds the removable unreachable branches in a switch statement.
   * Default case is not included in this list and should be checked separately
   *
   * @param reachableLabel The label that is reachable.
   * @param statement The switch statement to analyze.
   * @return A list of unreachable branches that can be safely removed.
   */
  public static @Unmodifiable List<PsiCaseLabelElement> findRemovableUnreachableBranches(PsiCaseLabelElement reachableLabel,
                                                                           PsiSwitchBlock statement) {
    PsiSwitchLabelStatementBase labelStatement = PsiTreeUtil.getParentOfType(reachableLabel, PsiSwitchLabelStatementBase.class);
    List<PsiSwitchLabelStatementBase> allBranches =
      PsiTreeUtil.getChildrenOfTypeAsList(statement.getBody(), PsiSwitchLabelStatementBase.class);
    boolean hasDefault = false;
    List<PsiCaseLabelElement> unreachableElements = new ArrayList<>();
    for (PsiSwitchLabelStatementBase branch : allBranches) {
      if (branch.isDefaultCase()) {
        hasDefault = true;
        continue;
      }
      PsiCaseLabelElementList elementList = branch.getCaseLabelElementList();
      if (elementList == null) {
        continue;
      }
      PsiCaseLabelElement[] elements = elementList.getElements();
      unreachableElements.addAll(Arrays.asList(elements));
    }
    unreachableElements.remove(reachableLabel);
    boolean canUnwrap = (statement instanceof PsiSwitchStatement && BreakConverter.from(statement) != null) ||
                        (statement instanceof PsiSwitchExpression &&
                         labelStatement instanceof PsiSwitchLabeledRuleStatement ruleStatement &&
                         ruleStatement.getBody() instanceof PsiExpressionStatement);
    if (canUnwrap) {
      return unreachableElements;
    }

    if (unreachableElements.isEmpty() || hasDefault) {
      return unreachableElements;
    }
    boolean isEnhancedSwitch = ExpressionUtil.isEnhancedSwitch(statement);
    if (isEnhancedSwitch) {
      PsiExpression expression = statement.getExpression();
      if (expression == null) {
        return List.of();
      }
      PsiType selectorType = expression.getType();
      if (selectorType == null) {
        return List.of();
      }
      List<PsiCaseLabelElement> toDelete = new ArrayList<>();
      for (int i = 0; i < unreachableElements.size(); i++) {
        PsiCaseLabelElement currentElement = unreachableElements.get(i);
        boolean isDominated = false;
        for (int j = i + 1; j < unreachableElements.size(); j++) {
          PsiCaseLabelElement nextElement = unreachableElements.get(j);
          isDominated = JavaPsiSwitchUtil.isDominated(currentElement, nextElement, selectorType);
          if (!isDominated) {
            break;
          }
        }

        if (!isDominated) {
          toDelete.add(currentElement);
        }
      }
      unreachableElements.removeAll(toDelete);
    }
    return unreachableElements;
  }

  /**
   * Evaluates the exhaustiveness state of a switch block.
   *
   * @param switchBlock                          the PsiSwitchBlock to evaluate
   * @param considerNestedDeconstructionPatterns flag indicating whether to consider nested deconstruction patterns. It is necessary to take into account,
   *                                             because nested deconstruction patterns don't cover null values
   * @return exhaustiveness state.
   */
  public static @NotNull SwitchExhaustivenessState evaluateSwitchCompleteness(@NotNull PsiSwitchBlock switchBlock,
                                                                              boolean considerNestedDeconstructionPatterns) {
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return SwitchExhaustivenessState.MALFORMED;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return SwitchExhaustivenessState.MALFORMED;
    PsiCodeBlock switchBody = switchBlock.getBody();
    if (switchBody == null) return SwitchExhaustivenessState.MALFORMED;
    List<PsiCaseLabelElement> labelElements = StreamEx.of(JavaPsiSwitchUtil.getSwitchBranches(switchBlock)).select(PsiCaseLabelElement.class)
      .filter(element -> !(element instanceof PsiDefaultCaseLabelElement)).toList();
    if (labelElements.isEmpty()) return SwitchExhaustivenessState.EMPTY;
    boolean needToCheckCompleteness = ExpressionUtil.isEnhancedSwitch(switchBlock);
    boolean isEnumSelector = JavaPsiSwitchUtil.getSwitchSelectorKind(selectorType) == JavaPsiSwitchUtil.SelectorKind.ENUM;
    if (ContainerUtil.find(labelElements, element -> JavaPsiPatternUtil.isUnconditionalForType(element, selectorType)) != null) {
      return SwitchExhaustivenessState.EXHAUSTIVE_NO_DEFAULT;
    }
    if (JavaPsiSwitchUtil.isBooleanSwitchWithTrueAndFalse(switchBlock)) {
      return SwitchExhaustivenessState.EXHAUSTIVE_NO_DEFAULT;
    }
    if (!needToCheckCompleteness && !isEnumSelector) return SwitchExhaustivenessState.INCOMPLETE;
    // It is necessary because deconstruction patterns don't cover cases 
    // when some of their components are null and deconstructionPattern too
    if (!considerNestedDeconstructionPatterns) {
      labelElements = ContainerUtil.filter(
        labelElements, label -> !(label instanceof PsiDeconstructionPattern deconstructionPattern &&
                                  ContainerUtil.or(
                                    deconstructionPattern.getDeconstructionList().getDeconstructionComponents(),
                                    component -> component instanceof PsiDeconstructionPattern)));
    }
    boolean hasError = hasExhaustivenessError(switchBlock, labelElements);
    // if a switch block is needed to check completeness and switch is incomplete we let highlighting to inform about it as it's a compilation error
    if (!hasError) {
      return SwitchExhaustivenessState.EXHAUSTIVE_CAN_ADD_DEFAULT;
    }
    if (needToCheckCompleteness) {
      return SwitchExhaustivenessState.UNNECESSARY;
    }
    return SwitchExhaustivenessState.INCOMPLETE;
  }

  /**
   * State of switch exhaustiveness.
   */
  public enum SwitchExhaustivenessState {
    /**
     * Switch is malformed and produces a compilation error (no body, no selector, etc.),
     * no exhaustiveness analysis is performed
     */
    MALFORMED,
    /**
     * Switch contains no labels (except probably default label)
     */
    EMPTY,
    /**
     * Switch should not be exhaustive (classic switch statement)
     */
    UNNECESSARY,
    /**
     * Switch is not exhaustive
     */
    INCOMPLETE,
    /**
     * Switch is exhaustive (complete), and adding a default branch would be a compilation error.
     * This includes a switch over boolean having both true and false branches, 
     * or a switch that has an unconditional pattern branch.
     */
    EXHAUSTIVE_NO_DEFAULT,
    /**
     * Switch is exhaustive (complete), but it's possible to add a default branch.
     */
    EXHAUSTIVE_CAN_ADD_DEFAULT
  }

  private static class LabelSearchVisitor extends JavaRecursiveElementWalkingVisitor {

    private final String m_labelName;
    private boolean m_used = false;

    LabelSearchVisitor(String name) {
      m_labelName = name;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (m_used) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitLabeledStatement(@NotNull PsiLabeledStatement statement) {
      final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
      final String labelText = labelIdentifier.getText();
      if (labelText.equals(m_labelName)) {
        m_used = true;
      }
    }

    public boolean isUsed() {
      return m_used;
    }
  }

  public static class IfStatementBranch {

    private final Set<String> topLevelVariables = new HashSet<>(3);
    private final LinkedList<String> comments = new LinkedList<>();
    private final LinkedList<String> statementComments = new LinkedList<>();
    private final List<PsiExpression> caseExpressions = new ArrayList<>(3);
    private final PsiStatement statement;
    private final boolean elseBranch;
    private boolean hasPattern;

    public IfStatementBranch(PsiStatement branch, boolean elseBranch) {
      statement = branch;
      this.elseBranch = elseBranch;
      calculateVariablesDeclared(statement);
    }

    public void addComment(String comment) {
      comments.addFirst(comment);
    }

    public void addStatementComment(String comment) {
      statementComments.addFirst(comment);
    }

    public void addCaseExpression(PsiExpression expression) {
      if (createPatternCaseText(expression) != null) {
        hasPattern = true;
      }
      caseExpressions.add(expression);
    }

    public boolean hasPattern() {
      return hasPattern;
    }

    public PsiStatement getStatement() {
      return statement;
    }

    public List<PsiExpression> getCaseExpressions() {
      return Collections.unmodifiableList(caseExpressions);
    }

    public boolean isElse() {
      return elseBranch;
    }

    public boolean topLevelDeclarationsConflictWith(IfStatementBranch testBranch) {
      return intersects(topLevelVariables, testBranch.topLevelVariables);
    }

    private static boolean intersects(Set<String> set1, Set<String> set2) {
      for (final String s : set1) {
        if (set2.contains(s)) {
          return true;
        }
      }
      return false;
    }

    public List<String> getComments() {
      return comments;
    }

    public List<String> getStatementComments() {
      return statementComments;
    }

    public void calculateVariablesDeclared(PsiStatement statement) {
      if (statement == null) {
        return;
      }
      if (statement instanceof PsiDeclarationStatement declarationStatement) {
        final PsiElement[] elements = declarationStatement.getDeclaredElements();
        for (PsiElement element : elements) {
          final PsiVariable variable = (PsiVariable)element;
          final String varName = variable.getName();
          topLevelVariables.add(varName);
        }
      }
      else if (statement instanceof PsiBlockStatement block) {
        final PsiCodeBlock codeBlock = block.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        for (PsiStatement statement1 : statements) {
          calculateVariablesDeclared(statement1);
        }
      }
    }
  }
}
