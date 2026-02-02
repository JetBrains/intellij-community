// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiCaseLabelElement;
import com.intellij.psi.PsiCaseLabelElementList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiPattern;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiQualifiedExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchExpression;
import com.intellij.psi.PsiSwitchLabelStatementBase;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExpressionUtil {
  /**
   * @return true if refExpression has no qualifier or has this qualifier corresponding to the inner most containing class
   */
  public static boolean isEffectivelyUnqualified(PsiReferenceExpression refExpression) {
    PsiExpression qualifier = PsiUtil.deparenthesizeExpression(refExpression.getQualifierExpression());
    if (qualifier == null) {
      return true;
    }
    if (qualifier instanceof PsiQualifiedExpression) {
      final PsiJavaCodeReferenceElement thisQualifier = ((PsiQualifiedExpression)qualifier).getQualifier();
      if (thisQualifier == null) return true;
      final PsiClass innerMostClass = PsiTreeUtil.getParentOfType(refExpression, PsiClass.class);
      return innerMostClass == thisQualifier.resolve();
    }
    return false;
  }

  /**
   * Checks if the given switch is enhanced.
   *
   * @param statement the switch to check
   * @return true if the switch is an enhanced switch, false otherwise
   */
  public static boolean isEnhancedSwitch(@NotNull PsiSwitchBlock statement) {
    if (statement instanceof PsiSwitchExpression) return true;

    PsiExpression selector = statement.getExpression();
    if (selector == null) return false;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return false;
    if (isEnhancedSelectorType(selectorType)) return true;
    PsiCodeBlock body = statement.getBody();
    if (body == null) return false;
    
    for (PsiStatement psiStatement : body.getStatements()) {
      if (psiStatement instanceof PsiSwitchLabelStatementBase) {
        PsiSwitchLabelStatementBase labelStatementBase = (PsiSwitchLabelStatementBase)psiStatement;
        PsiCaseLabelElementList elementList = labelStatementBase.getCaseLabelElementList();
        if (elementList == null) continue;
        PsiCaseLabelElement[] elements = elementList.getElements();
        for (PsiCaseLabelElement caseLabelElement : elements) {
          if (caseLabelElement != null) {
            if (caseLabelElement instanceof PsiPattern || isNullLiteral(caseLabelElement)) return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * @param element expression to check
   * @return true if the expression is a null literal (possibly parenthesized or cast)
   */
  @Contract("null -> false")
  public static boolean isNullLiteral(@Nullable PsiElement element) {
    if (!(element instanceof PsiExpression)) return false;
    PsiExpression deparenthesized = PsiUtil.deparenthesizeExpression((PsiExpression)element);
    return deparenthesized instanceof PsiLiteralExpression && ((PsiLiteralExpression)deparenthesized).getValue() == null;
  }

  /**
   * @param type type of switch selector to check
   * @return true if this type of switch selector is a type of enhanced switch selector; 
   * false if it's a classic (Java 7) switch selector type.
   */
  public static boolean isEnhancedSelectorType(@NotNull PsiType type) {
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
}
