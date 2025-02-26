// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * Utility methods to support switch
 */
public final class JavaPsiSwitchUtil {


  /**
   * Returns the selector kind based on the type.
   * <p> 
   * The result may depend on the type language level for boxed primitive types.
   * E.g., if selector type is {@link Double} then {@link SelectorKind#DOUBLE} will be returned
   * if primitives in patterns are supported, but {@link SelectorKind#CLASS_OR_ARRAY} will be returned otherwise.
   * <p>
   * It's not checked whether this particular kind is supported at a given location. 
   * It's up to the caller to check this using the {@link SelectorKind#getFeature()} method.
   * 
   * @param selectorType type of switch selector expression
   * @return kind of selector 
   */
  public static @NotNull SelectorKind getSwitchSelectorKind(@NotNull PsiType selectorType) {
    if (TypeConversionUtil.getTypeRank(selectorType) <= TypeConversionUtil.INT_RANK) {
      return SelectorKind.INT;
    }
    PsiType unboxedType = selectorType instanceof PsiClassType && 
                          JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.isSufficient(((PsiClassType)selectorType).getLanguageLevel()) ? 
                          PsiPrimitiveType.getOptionallyUnboxedType(selectorType) : selectorType;
    if (unboxedType != null) {
      if (unboxedType.equals(PsiTypes.longType())) {
        return SelectorKind.LONG;
      }
      else if (unboxedType.equals(PsiTypes.booleanType())) {
        return SelectorKind.BOOLEAN;
      }
      else if (unboxedType.equals(PsiTypes.floatType())) {
        return SelectorKind.FLOAT;
      }
      else if (unboxedType.equals(PsiTypes.doubleType())) {
        return SelectorKind.DOUBLE;
      }
    }
    if (TypeConversionUtil.isPrimitiveAndNotNull(selectorType)) {
      return SelectorKind.INVALID;
    }
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(selectorType);
    if (psiClass != null) {
      if (psiClass.isEnum()) return SelectorKind.ENUM;
      String fqn = psiClass.getQualifiedName();
      if (Comparing.strEqual(fqn, CommonClassNames.JAVA_LANG_STRING)) {
        return SelectorKind.STRING;
      }
    }
    return SelectorKind.CLASS_OR_ARRAY;
  }

  /**
   * @param block switch block to check
   * @return true if the block contains at least one case label (including default)
   */
  @Contract(pure = true)
  public static boolean hasAnyCaseLabels(@NotNull PsiSwitchBlock block) {
    PsiCodeBlock body = block.getBody();
    if (body == null) return false;
    for (PsiElement st = body.getFirstChild(); st != null; st = st.getNextSibling()) {
      if (!(st instanceof PsiSwitchLabelStatementBase labelStatement)) continue;
      if (labelStatement.isDefaultCase()) {
        return true;
      }
      PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
      if (labelElementList != null && labelElementList.getElementCount() > 0) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable PsiEnumConstant getEnumConstant(@Nullable PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      return ObjectUtils.tryCast(((PsiReferenceExpression)element).resolve(), PsiEnumConstant.class);
    }
    return null;
  }

  private static @Nullable Object getBranchConstant(@NotNull PsiCaseLabelElement labelElement, @NotNull PsiType selectorType) {
    if (labelElement instanceof PsiExpression expr) {
      if (expr instanceof PsiReferenceExpression) {
        PsiEnumConstant enumConstant = getEnumConstant(expr);
        if (enumConstant != null) {
          return enumConstant;
        }
      }
      Object operand = JavaPsiFacade.getInstance(labelElement.getProject()).getConstantEvaluationHelper()
        .computeConstantExpression(labelElement, false);
      if (operand != null) {
        if (operand instanceof Boolean && getSwitchSelectorKind(selectorType) == SelectorKind.BOOLEAN) {
          return ((Boolean)operand).booleanValue();
        }
        return ConstantExpressionUtil.computeCastTo(operand, selectorType);
      }
      if (labelElement instanceof PsiLiteralExpression && ((PsiLiteralExpression)labelElement).getType() == PsiTypes.nullType()) {
        return SwitchSpecialValue.NULL_VALUE;
      }
    }
    else if (labelElement instanceof PsiDefaultCaseLabelElement) {
      return SwitchSpecialValue.DEFAULT_VALUE;
    }
    else if (JavaPsiPatternUtil.isUnconditionalForType(labelElement, selectorType)) {
      return SwitchSpecialValue.UNCONDITIONAL_PATTERN;
    }
    return null;
  }

  /**
   * @param block switch block to analyze
   * @return a map where keys are switch constants and values are the corresponding PSI elements 
   * (either {@link PsiCaseLabelElement}, or 'default' keyword). 
   * Some special values listed in {@link SwitchSpecialValue} enum could be returned as well. 
   * Pattern labels are ignored and not returned by this method.
   * It's useful to check for duplicate branches: if a single constant is mapped to more than one PSI element, then such a switch
   * is not well-formed.
   */
  public static @NotNull MultiMap<Object, PsiElement> getValuesAndLabels(@NotNull PsiSwitchBlock block) {
    MultiMap<Object, PsiElement> elementsToCheckDuplicates = new MultiMap<>();
    PsiCodeBlock body = block.getBody();
    if (body == null) return elementsToCheckDuplicates;
    PsiExpression selector = block.getExpression();
    if (selector == null) return elementsToCheckDuplicates;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return elementsToCheckDuplicates;
    for (PsiStatement st : body.getStatements()) {
      if (!(st instanceof PsiSwitchLabelStatementBase labelStatement)) continue;
      if (labelStatement.isDefaultCase()) {
        elementsToCheckDuplicates.putValue(SwitchSpecialValue.DEFAULT_VALUE, requireNonNull(labelStatement.getFirstChild()));
        continue;
      }
      PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
      if (labelElementList == null) continue;
      for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
        Object constant = getBranchConstant(labelElement, selectorType);
        if (constant != null) {
          elementsToCheckDuplicates.putValue(constant, labelElement);
        }
      }
    }
    return elementsToCheckDuplicates;
  }

  /**
   * Determines if the given case label element is dominated by another case label element according to JEP 440-441
   *
   * @param overWhom The case label element that may dominate.
   * @param who The case label element that may be dominated.
   * @param selectorType The type used to select the case label element.
   * @return {@code true} if the 'overWhom' case label element dominates the 'who' case label element, {@code false} otherwise.
   */
  public static boolean isDominated(@NotNull PsiCaseLabelElement overWhom,
                                    @NotNull PsiElement who,
                                    @NotNull PsiType selectorType) {
    boolean isOverWhomUnconditionalForSelector = JavaPsiPatternUtil.isUnconditionalForType(overWhom, selectorType);
    if (!isOverWhomUnconditionalForSelector &&
        ((!(overWhom instanceof PsiExpression expression) || JavaPsiExpressionUtil.isNullLiteral(expression)) &&
         who instanceof PsiKeyword &&
         PsiKeyword.DEFAULT.equals(who.getText()) || isInCaseNullDefaultLabel(who))) {
      // JEP 440-441
      // A 'default' label dominates a case label with a case pattern,
      // and it also dominates a case label with a null case constant.
      // A 'case null, default' label dominates all other switch labels.
      return true;
    }
    if (who instanceof PsiCaseLabelElement currentElement) {
      if (JavaPsiPatternUtil.isGuarded(currentElement)) return false;
      if (isConstantLabelElement(overWhom)) {
        PsiExpression constExpr = ObjectUtils.tryCast(overWhom, PsiExpression.class);
        assert constExpr != null;
        if (JavaPsiPatternUtil.dominatesOverConstant(currentElement, constExpr.getType())) {
          return true;
        }
      }
      else {
        if (JavaPsiPatternUtil.dominates(currentElement, overWhom)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isInCaseNullDefaultLabel(@NotNull PsiElement element) {
    PsiCaseLabelElementList list = ObjectUtils.tryCast(element.getParent(), PsiCaseLabelElementList.class);
    if (list == null || list.getElementCount() != 2) return false;
    PsiCaseLabelElement[] elements = list.getElements();
    return elements[0] instanceof PsiExpression expr &&
           JavaPsiExpressionUtil.isNullLiteral(expr) &&
           elements[1] instanceof PsiDefaultCaseLabelElement;
  }

  private static boolean isConstantLabelElement(@NotNull PsiCaseLabelElement labelElement) {
    Object value = JavaPsiFacade.getInstance(labelElement.getProject()).getConstantEvaluationHelper()
      .computeConstantExpression(labelElement, false);
    return value != null || isEnumConstant(labelElement);
  }

  private static boolean isEnumConstant(@NotNull PsiCaseLabelElement element) {
    return getEnumConstant(element) != null;
  }

  /**
   * @param block switch block to analyze
   * @return map of labels where keys are dominated labels, and values are dominating labels (or 'default' keyword from default label,
   * which may dominate over any other label).
   * Only labels for which domination rules are violated will be returned.
   */
  public static @NotNull Map<PsiCaseLabelElement, PsiElement> findDominatedLabels(@NotNull PsiSwitchBlock block) {
    PsiCodeBlock body = block.getBody();
    if (body == null) return Collections.emptyMap();
    PsiExpression selector = block.getExpression();
    if (selector == null) return Collections.emptyMap();
    PsiType selectorType = selector.getType();
    if (selectorType == null) return Collections.emptyMap();

    List<PsiElement> elementsToCheckDominance = new ArrayList<>();
    for (PsiStatement st : body.getStatements()) {
      if (!(st instanceof PsiSwitchLabelStatementBase labelStatement)) continue;
      if (labelStatement.isDefaultCase()) {
        elementsToCheckDominance.add(requireNonNull(labelStatement.getFirstChild()));
        continue;
      }
      PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
      if (labelElementList == null) continue;
      for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
        if (shouldConsiderForDominance(labelElement)) {
          elementsToCheckDominance.add(labelElement);
        }
      }
    }
    Map<PsiCaseLabelElement, PsiElement> result = new HashMap<>();
    for (int i = 0; i < elementsToCheckDominance.size() - 1; i++) {
      PsiElement current = elementsToCheckDominance.get(i);
      if (result.containsKey(current)) continue;
      for (int j = i + 1; j < elementsToCheckDominance.size(); j++) {
        PsiElement next = elementsToCheckDominance.get(j);
        if (!(next instanceof PsiCaseLabelElement nextElement)) continue;
        boolean dominated = isDominated(nextElement, current, selectorType);
        if (dominated) {
          result.put(nextElement, current);
        }
      }
    }
    return result;
  }

  private static boolean shouldConsiderForDominance(@NotNull PsiCaseLabelElement labelElement) {
    if (labelElement instanceof PsiPattern) return true;
    if (labelElement instanceof PsiExpression) {
      boolean isNullType = ExpressionUtil.isNullType(labelElement);
      if (isNullType && isInCaseNullDefaultLabel(labelElement)) {
        // JEP 432
        // A 'case null, default' label dominates all other switch labels.
        //
        // In this case, only the 'default' case will be added to the elements checked for dominance
        return false;
      }
      return isNullType || isConstantLabelElement(labelElement);
    }
    return labelElement instanceof PsiDefaultCaseLabelElement;
  }

  /**
   * Kinds of switch selector
   * @see #getSwitchSelectorKind(PsiType) 
   */
  public enum SelectorKind {
    /**
     * Classic Java 1.0 int selector (also covers byte, short, and char, thanks to primitive widening)
     */
    INT(null),
    /**
     * Java 1.5 enum selector
     */
    ENUM(JavaFeature.ENUMS),
    /**
     * Java 1.7 string selector
     */
    STRING(JavaFeature.STRING_SWITCH),
    /**
     * Generic pattern selector
     */
    CLASS_OR_ARRAY(JavaFeature.PATTERNS_IN_SWITCH),
    /**
     * Primitive selector
     */
    BOOLEAN(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS), 
    LONG(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS), 
    FLOAT(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS), 
    DOUBLE(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS),
    /**
     * Invalid selector type (like void)
     */
    INVALID(null);
    
    private final @Nullable JavaFeature myFeature;

    SelectorKind(@Nullable JavaFeature feature) { myFeature = feature; }

    /**
     * @return java feature required for this selector kind; null if it's always available or non-applicable
     */
    public @Nullable JavaFeature getFeature() {
      return myFeature;
    }
  }

  /**
   * Special values for switch labels that could be returned from {@link #getValuesAndLabels(PsiSwitchBlock)}
   */
  public enum SwitchSpecialValue {
    /**
     * Unconditional pattern
     */
    UNCONDITIONAL_PATTERN,
    /**
     * Default value (either default branch, or case default if supported)
     */
    DEFAULT_VALUE,
    /**
     * Null value (like 'case null')
     */
    NULL_VALUE
  }
}
