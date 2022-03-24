// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class JavaPsiPatternUtil {
  /**
   * @param expression expression to search pattern variables in
   * @return list of pattern variables declared within an expression that could be visible outside of given expression.
   */
  @Contract(pure = true)
  public static @NotNull List<PsiPatternVariable> getExposedPatternVariables(@NotNull PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    boolean parentMayAccept =
      parent instanceof PsiPrefixExpression && ((PsiPrefixExpression)parent).getOperationTokenType().equals(JavaTokenType.EXCL) ||
      parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType().equals(JavaTokenType.ANDAND) ||
      parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType().equals(JavaTokenType.OROR) ||
      parent instanceof PsiConditionalExpression || parent instanceof PsiIfStatement || parent instanceof PsiConditionalLoopStatement;
    if (!parentMayAccept) {
      return Collections.emptyList();
    }
    List<PsiPatternVariable> list = new ArrayList<>();
    collectPatternVariableCandidates(expression, expression, list, false);
    return list;
  }

  /**
   * @param expression expression to search pattern variables in
   * @return list of pattern variables declared within an expression that could be visible outside of given expression
   * under some other parent (e.g. under PsiIfStatement).
   */
  @Contract(pure = true)
  public static @NotNull List<PsiPatternVariable> getExposedPatternVariablesIgnoreParent(@NotNull PsiExpression expression) {
    List<PsiPatternVariable> list = new ArrayList<>();
    collectPatternVariableCandidates(expression, expression, list, true);
    return list;
  }

  /**
   * @param variable pattern variable
   * @return effective initializer expression for the variable; null if cannot be determined
   */
  public static @Nullable String getEffectiveInitializerText(@NotNull PsiPatternVariable variable) {
    PsiPattern pattern = variable.getPattern();
    PsiInstanceOfExpression instanceOf = ObjectUtils.tryCast(pattern.getParent(), PsiInstanceOfExpression.class);
    if (instanceOf == null) return null;
    if (pattern instanceof PsiTypeTestPattern) {
      PsiExpression operand = instanceOf.getOperand();
      PsiTypeElement checkType = ((PsiTypeTestPattern)pattern).getCheckType();
      if (checkType == null) return null;
      if (checkType.getType().equals(operand.getType())) {
        return operand.getText();
      }
      return "(" + checkType.getText() + ")" + operand.getText();
    }
    return null;
  }

  @Contract(value = "null -> null", pure = true)
  public static @Nullable PsiPattern skipParenthesizedPatternDown(PsiPattern pattern) {
    while (pattern instanceof PsiParenthesizedPattern) {
      pattern = ((PsiParenthesizedPattern)pattern).getPattern();
    }
    return pattern;
  }

  public static PsiElement skipParenthesizedPatternUp(PsiElement parent) {
    while (parent instanceof PsiParenthesizedPattern) {
      parent = parent.getParent();
    }
    return parent;
  }

  /**
   * @param pattern
   * @return extracted pattern variable or null if the pattern is incomplete or unknown
   */
  @Contract(value = "null -> null", pure = true)
  @Nullable
  public static PsiPatternVariable getPatternVariable(@Nullable PsiPattern pattern) {
    if (pattern instanceof PsiGuardedPattern) {
      return getPatternVariable(((PsiGuardedPattern)pattern).getPrimaryPattern());
    }
    if (pattern instanceof PsiParenthesizedPattern) {
      return getPatternVariable(((PsiParenthesizedPattern)pattern).getPattern());
    }
    if (pattern instanceof PsiTypeTestPattern) {
      return ((PsiTypeTestPattern)pattern).getPatternVariable();
    }
    return null;
  }

  /**
   * @param pattern
   * @return type of variable in pattern, or null if pattern is incomplete
   */
  @Contract(value = "null -> null", pure = true)
  @Nullable
  public static PsiType getPatternType(@Nullable PsiPattern pattern) {
    if (pattern == null) return null;
    if (pattern instanceof PsiGuardedPattern) {
      return getPatternType(((PsiGuardedPattern)pattern).getPrimaryPattern());
    }
    else if (pattern instanceof PsiParenthesizedPattern) {
      return getPatternType(((PsiParenthesizedPattern)pattern).getPattern());
    }
    else if (pattern instanceof PsiTypeTestPattern) {
      PsiTypeElement checkType = ((PsiTypeTestPattern)pattern).getCheckType();
      if (checkType != null) return checkType.getType();
    }
    return null;
  }

  /**
   * 14.30.3 Pattern Totality and Dominance
   */
  @Contract(value = "null, _ -> false", pure = true)
  public static boolean isTotalForType(@Nullable PsiPattern pattern, @NotNull PsiType type) {
    if (pattern == null) return false;
    if (pattern instanceof PsiGuardedPattern) {
      PsiGuardedPattern guarded = (PsiGuardedPattern)pattern;
      Object constVal = evaluateConstant(guarded.getGuardingExpression());
      return isTotalForType(guarded.getPrimaryPattern(), type) && Boolean.TRUE.equals(constVal);
    }
    else if (pattern instanceof PsiParenthesizedPattern) {
      return isTotalForType(((PsiParenthesizedPattern)pattern).getPattern(), type);
    }
    else if (pattern instanceof PsiTypeTestPattern) {
      type = TypeConversionUtil.erasure(type);
      PsiType baseType = TypeConversionUtil.erasure(getPatternType(pattern));
      if (type instanceof PsiArrayType || baseType instanceof PsiArrayType) {
        return baseType != null && TypeConversionUtil.isAssignable(baseType, type);
      }
      PsiClass typeClass = PsiTypesUtil.getPsiClass(type);
      PsiClass baseTypeClass = PsiTypesUtil.getPsiClass(baseType);
      return typeClass != null && baseTypeClass != null && InheritanceUtil.isInheritorOrSelf(typeClass, baseTypeClass, true);
    }
    return false;
  }

  /**
   * 14.30.3 Pattern Totality and Dominance
   */
  @Contract(value = "null, _ -> false", pure = true)
  public static boolean dominates(@Nullable PsiPattern who, @NotNull PsiPattern overWhom) {
    if (who == null) return false;
    if (overWhom instanceof PsiGuardedPattern) {
      if (who instanceof PsiTypeTestPattern) {
        PsiType whoType = getPatternType(who);
        PsiType overWhomType = getPatternType(overWhom);
        if (whoType != null && overWhomType != null && whoType.equalsToText(overWhomType.getCanonicalText())) {
          return true;
        }
      }
      else if (who instanceof PsiParenthesizedPattern) {
        return dominates(((PsiParenthesizedPattern)who).getPattern(), overWhom);
      }
      else if (who instanceof PsiGuardedPattern) {
        boolean dominates = dominates(((PsiGuardedPattern)who).getPrimaryPattern(), overWhom);
        if (!dominates) return false;
        Object constVal = evaluateConstant(((PsiGuardedPattern)who).getGuardingExpression());
        return Boolean.TRUE.equals(constVal);
      }
      else {
        return false;
      }
      return dominates(who, ((PsiGuardedPattern)overWhom).getPrimaryPattern());
    }
    else if (overWhom instanceof PsiParenthesizedPattern) {
      PsiPattern pattern = ((PsiParenthesizedPattern)overWhom).getPattern();
      if (pattern == null) return false;
      return dominates(who, pattern);
    }
    else if (overWhom instanceof PsiTypeTestPattern) {
      PsiType overWhomType = getPatternType(overWhom);
      return overWhomType != null && isTotalForType(who, overWhomType);
    }
    return false;
  }

  /**
   * 14.11.1 Switch Blocks
   */
  @Contract(value = "_,null -> false", pure = true)
  public static boolean dominates(@NotNull PsiPattern who, @Nullable PsiType overWhom) {
    if (overWhom == null) return false;
    PsiType whoType = TypeConversionUtil.erasure(getPatternType(who));
    if (whoType == null) return false;
    PsiType overWhomType = null;
    if (overWhom instanceof PsiPrimitiveType) {
      overWhomType = ((PsiPrimitiveType)overWhom).getBoxedType(who);
    }
    else if (overWhom instanceof PsiClassType) {
      overWhomType = overWhom;
    }
    return overWhomType != null && TypeConversionUtil.areTypesConvertible(overWhomType, whoType);
  }

  private static void collectPatternVariableCandidates(@NotNull PsiExpression scope, @NotNull PsiExpression expression,
                                                       Collection<PsiPatternVariable> candidates, boolean strict) {
    while (true) {
      if (expression instanceof PsiParenthesizedExpression) {
        expression = ((PsiParenthesizedExpression)expression).getExpression();
      }
      else if (expression instanceof PsiPrefixExpression &&
               ((PsiPrefixExpression)expression).getOperationTokenType().equals(JavaTokenType.EXCL)) {
        expression = ((PsiPrefixExpression)expression).getOperand();
      }
      else {
        break;
      }
    }
    if (expression instanceof PsiInstanceOfExpression) {
      PsiPattern pattern = ((PsiInstanceOfExpression)expression).getPattern();
      if (pattern instanceof PsiTypeTestPattern) {
        PsiPatternVariable variable = ((PsiTypeTestPattern)pattern).getPatternVariable();
        if (variable != null && !PsiTreeUtil.isAncestor(scope, variable.getDeclarationScope(), strict)) {
          candidates.add(variable);
        }
      }
    }
    if (expression instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          collectPatternVariableCandidates(scope, operand, candidates, strict);
        }
      }
    }
  }

  @Nullable
  private static Object evaluateConstant(@Nullable PsiExpression expression) {
    if (expression == null) return null;
    return JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper()
      .computeConstantExpression(expression, false);
  }
}
