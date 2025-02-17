// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods to support switch
 */
public final class JavaPsiSwitchUtil {

  /**
   * Checks if the given switch is enhanced.
   *
   * @param statement the switch to check
   * @return true if the switch is an enhanced switch, false otherwise
   */
  public static boolean isEnhancedSwitch(@NotNull PsiSwitchBlock statement) {
    if(statement instanceof PsiSwitchExpression) return true;

    PsiExpression selector = statement.getExpression();
    if (selector == null) {
      return false;
    }
    PsiType selectorType = selector.getType();
    if (selectorType == null) {
      return false;
    }
    PsiCodeBlock body = statement.getBody();
    if (body == null) {
      return false;
    }
    List<PsiCaseLabelElement> cases = new ArrayList<>();
    for (PsiStatement psiStatement : body.getStatements()) {
      if (psiStatement instanceof PsiSwitchLabelStatementBase) {
        PsiSwitchLabelStatementBase labelStatementBase = (PsiSwitchLabelStatementBase)psiStatement;
        PsiCaseLabelElementList elementList = labelStatementBase.getCaseLabelElementList();
        if (elementList == null) {
          continue;
        }
        PsiCaseLabelElement[] elements = elementList.getElements();
        for (PsiCaseLabelElement caseLabelElement : elements) {
          if (caseLabelElement != null) {
            cases.add(caseLabelElement);
          }
        }
      }
    }
    return isEnhancedSwitch(cases, selectorType);
  }

  public static boolean isEnhancedSwitch(@NotNull List<? extends PsiCaseLabelElement> labelElements, @NotNull PsiType selectorType) {
    if (isEnhancedSelectorType(selectorType)) return true;
    return ContainerUtil.exists(labelElements, st -> st instanceof PsiPattern || isNullType(st));
  }


  private static boolean isNullType(@NotNull PsiElement element) {
    return element instanceof PsiExpression && TypeConversionUtil.isNullType(((PsiExpression)element).getType());
  }

  private static boolean isEnhancedSelectorType(@NotNull PsiType type) {
    PsiPrimitiveType unboxedType = PsiPrimitiveType.getOptionallyUnboxedType(type);
    if (unboxedType != null &&
        (unboxedType.equals(PsiTypes.booleanType()) ||
         unboxedType.equals(PsiTypes.floatType()) ||
         unboxedType.equals(PsiTypes.doubleType()) ||
         unboxedType.equals(PsiTypes.longType()))) {
      return true;
    }
    if (TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.INT_RANK) return false;
    if (TypeConversionUtil.isPrimitiveAndNotNull(type)) return false;
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (psiClass != null) {
      if (psiClass.isEnum()) return false;
      String fqn = psiClass.getQualifiedName();
      if (Comparing.strEqual(fqn, CommonClassNames.JAVA_LANG_STRING)) return false;
    }
    return true;
  }

  /**
   * Returns the selector kind based on the type.
   * <p> 
   * The result may depend on type language level for boxed primitive types.
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
     * @return java feature required for this selector kind; null if it's always available
     */
    public @Nullable JavaFeature getFeature() {
      return myFeature;
    }
  }
}
