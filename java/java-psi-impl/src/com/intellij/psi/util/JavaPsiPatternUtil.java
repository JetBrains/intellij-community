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

  @Contract(value = "null -> null", pure = true)
  @Nullable
  public static PsiPrimaryPattern getTypedPattern(@Nullable PsiCaseLabelElement element) {
    if (element instanceof PsiGuardedPattern) {
      return getTypedPattern(((PsiGuardedPattern)element).getPrimaryPattern());
    }
    else if (element instanceof PsiPatternGuard) {
      return getTypedPattern(((PsiPatternGuard)element).getPattern());
    }
    else if (element instanceof PsiParenthesizedPattern) {
      return getTypedPattern(((PsiParenthesizedPattern)element).getPattern());
    }
    else if (element instanceof PsiDeconstructionPattern) {
      return ((PsiDeconstructionPattern)element);
    }
    else if (element instanceof PsiTypeTestPattern) {
      return ((PsiTypeTestPattern)element);
    }
    else {
      return null;
    }
  }

  /**
   * @param pattern
   * @return type of variable in pattern, or null if pattern is incomplete
   */
  @Contract(value = "null -> null", pure = true)
  @Nullable
  public static PsiType getPatternType(@Nullable PsiCaseLabelElement pattern) {
    PsiTypeElement typeElement = getPatternTypeElement(pattern);
    if (typeElement == null) return null;
    return typeElement.getType();
  }

  public static @Nullable PsiTypeElement getPatternTypeElement(@Nullable PsiCaseLabelElement pattern) {
    if (pattern == null) return null;
    if (pattern instanceof PsiGuardedPattern) {
      return getPatternTypeElement(((PsiGuardedPattern)pattern).getPrimaryPattern());
    }
    else if (pattern instanceof PsiPatternGuard) {
      return getPatternTypeElement(((PsiPatternGuard)pattern).getPattern());
    }
    else if (pattern instanceof PsiParenthesizedPattern) {
      return getPatternTypeElement(((PsiParenthesizedPattern)pattern).getPattern());
    }
    else if (pattern instanceof PsiDeconstructionPattern) {
      return ((PsiDeconstructionPattern)pattern).getTypeElement();
    }
    else if (pattern instanceof PsiTypeTestPattern) {
      return ((PsiTypeTestPattern)pattern).getCheckType();
    }
    return null;
  }

  @Contract(value = "null, _ -> false", pure = true)
  public static boolean isTotalForType(@Nullable PsiCaseLabelElement pattern, @NotNull PsiType type) {
    return isTotalForType(pattern, type, true);
  }

  public static boolean isTotalForType(@Nullable PsiCaseLabelElement pattern, @NotNull PsiType type, boolean checkComponents) {
    if (pattern == null) return false;
    if (pattern instanceof PsiPatternGuard) {
      PsiPatternGuard guarded = (PsiPatternGuard)pattern;
      Object constVal = evaluateConstant(guarded.getGuardingExpression());
      return isTotalForType(guarded.getPattern(), type, checkComponents) && Boolean.TRUE.equals(constVal);
    }
    if (pattern instanceof PsiGuardedPattern) {
      PsiGuardedPattern guarded = (PsiGuardedPattern)pattern;
      Object constVal = evaluateConstant(guarded.getGuardingExpression());
      return isTotalForType(guarded.getPrimaryPattern(), type, checkComponents) && Boolean.TRUE.equals(constVal);
    }
    else if (pattern instanceof PsiParenthesizedPattern) {
      return isTotalForType(((PsiParenthesizedPattern)pattern).getPattern(), type, checkComponents);
    }
    else if (pattern instanceof PsiDeconstructionPattern) {
      if (!dominates(getPatternType(pattern), type)) return false;
      if (checkComponents) {
        PsiPattern[] patternComponents = ((PsiDeconstructionPattern)pattern).getDeconstructionList().getDeconstructionComponents();
        PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (selectorClass == null) return false;
        PsiRecordComponent[] recordComponents = selectorClass.getRecordComponents();
        if (patternComponents.length != recordComponents.length) return false;
        for (int i = 0; i < patternComponents.length; i++) {
          PsiPattern patternComponent = patternComponents[i];
          PsiType componentType = recordComponents[i].getType();
          if (!isTotalForType(patternComponent, componentType, true)) {
            return false;
          }
        }
      }
      return true;
    }
    else if (pattern instanceof PsiTypeTestPattern) {
      return dominates(getPatternType(pattern), type);
    }
    return false;
  }

  public static boolean dominates(@Nullable PsiType who, @Nullable PsiType overWhom) {
    if (who == null || overWhom == null) return false;
    if (who.getCanonicalText().equals(overWhom.getCanonicalText())) return true;
    overWhom = TypeConversionUtil.erasure(overWhom);
    PsiType baseType = TypeConversionUtil.erasure(who);
    if (overWhom instanceof PsiArrayType || baseType instanceof PsiArrayType) {
      return baseType != null && TypeConversionUtil.isAssignable(baseType, overWhom);
    }
    PsiClass typeClass = PsiTypesUtil.getPsiClass(overWhom);
    PsiClass baseTypeClass = PsiTypesUtil.getPsiClass(baseType);
    return typeClass != null && baseTypeClass != null && InheritanceUtil.isInheritorOrSelf(typeClass, baseTypeClass, true);
  }

  /**
   * 14.30.3 Pattern Totality and Dominance
   */
  @Contract(value = "null, _ -> false", pure = true)
  public static boolean dominates(@Nullable PsiCaseLabelElement who, @NotNull PsiCaseLabelElement overWhom) {
    if (who == null) return false;
    PsiType overWhomType = getPatternType(overWhom);
    if (overWhomType == null || !isTotalForType(who, overWhomType, false)) {
      return false;
    }
    PsiDeconstructionPattern whoDeconstruction = findDeconstructionPattern(who);
    if (whoDeconstruction != null) {
      PsiDeconstructionPattern overWhomDeconstruction = findDeconstructionPattern(overWhom);
      return dominatesComponents(whoDeconstruction, overWhomDeconstruction);
    }
    return true;
  }

  private static boolean dominatesComponents(@NotNull PsiDeconstructionPattern who, @Nullable PsiDeconstructionPattern overWhom) {
    if (overWhom == null) return false;
    PsiPattern[] whoComponents = who.getDeconstructionList().getDeconstructionComponents();
    PsiPattern[] overWhomComponents = overWhom.getDeconstructionList().getDeconstructionComponents();
    if (whoComponents.length != overWhomComponents.length) return false;
    for (int i = 0; i < whoComponents.length; i++) {
      PsiPattern whoComponent = whoComponents[i];
      PsiPattern overWhomComponent = overWhomComponents[i];
      if (!dominates(whoComponent, overWhomComponent)) return false;
    }
    return true;
  }

  public static @Nullable PsiDeconstructionPattern findDeconstructionPattern(@Nullable PsiCaseLabelElement element) {
    if (element instanceof PsiParenthesizedPattern) {
      return findDeconstructionPattern(((PsiParenthesizedPattern)element).getPattern());
    }
    else if (element instanceof PsiPatternGuard) {
      return findDeconstructionPattern(((PsiPatternGuard)element).getPattern());
    }
    else if (element instanceof PsiDeconstructionPattern) {
      return (PsiDeconstructionPattern)element;
    }
    else {
      return null;
    }
  }

  /**
   * 14.11.1 Switch Blocks
   */
  @Contract(value = "_,null -> false", pure = true)
  public static boolean dominates(@NotNull PsiCaseLabelElement who, @Nullable PsiType overWhom) {
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
  public static Object evaluateConstant(@Nullable PsiExpression expression) {
    if (expression == null) return null;
    return JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper()
      .computeConstantExpression(expression, false);
  }
}
