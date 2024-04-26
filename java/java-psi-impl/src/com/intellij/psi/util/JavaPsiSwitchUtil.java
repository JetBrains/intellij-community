// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

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
    return isEnhancedSwitch(cases, isClassSelectorType(selectorType));
  }

  public static boolean isEnhancedSwitch(@NotNull List<? extends PsiCaseLabelElement> labelElements, boolean selectorIsTypeOrClass) {
    if (selectorIsTypeOrClass) return true;
    return ContainerUtil.exists(labelElements, st -> st instanceof PsiPattern || isNullType(st));
  }


  private static boolean isNullType(@NotNull PsiElement element) {
    return element instanceof PsiExpression && TypeConversionUtil.isNullType(((PsiExpression)element).getType());
  }

  private static boolean isClassSelectorType(@NotNull PsiType type) {
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
