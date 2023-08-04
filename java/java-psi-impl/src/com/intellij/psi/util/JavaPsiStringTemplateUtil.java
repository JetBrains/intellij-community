// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.psi.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING_TEMPLATE;

public final class JavaPsiStringTemplateUtil {
  /**
   * @param processor template processor expression to check
   * @return true if the supplied processor is the standard StringTemplate.STR processor; false otherwise
   */
  @Contract("null -> false")
  public static boolean isStrTemplate(@Nullable PsiExpression processor) {
    processor = PsiUtil.skipParenthesizedExprDown(processor);
    if (processor instanceof PsiReferenceExpression) {
      PsiElement target = ((PsiReferenceExpression)processor).resolve();
      if (target instanceof PsiField) {
        PsiField field = (PsiField)target;
        if (field.getName().equals("STR")) {
          PsiClass containingClass = field.getContainingClass();
          return containingClass != null && JAVA_LANG_STRING_TEMPLATE.equals(containingClass.getQualifiedName());
        }
      }
    }
    return false;
  }
}
