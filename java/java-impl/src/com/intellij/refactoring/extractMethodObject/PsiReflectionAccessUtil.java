// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public class PsiReflectionAccessUtil {
  public static boolean isAccessibleMember(@NotNull PsiMember classMember) {
    return classMember.hasModifierProperty(PsiModifier.PUBLIC) && isAccessible(classMember.getContainingClass());
  }

  @Contract("null -> false")
  public static boolean isAccessible(@Nullable PsiClass psiClass) {
    if (psiClass == null) return false;
    while (psiClass != null) {
      if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return false;
      }

      psiClass = psiClass.getContainingClass();
    }

    return true;
  }

  @Nullable
  public static String extractQualifier(@NotNull PsiReferenceExpression referenceExpression) {
    PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
    PsiType expressionType = qualifierExpression != null ? qualifierExpression.getType() : null;
    return expressionType == null ? null : qualifierExpression.getText();
  }

  @Contract("null -> null")
  @Nullable
  public static PsiClass nearestAccessedClass(@Nullable PsiClass psiClass) {
    while (psiClass != null && !psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
      psiClass = psiClass.getSuperClass();
    }

    return psiClass;
  }

  @Nullable
  public static String getAccessibleReturnType(@Nullable PsiType type) {
    PsiClass psiClass = nearestAccessedClass(PsiUtil.resolveClassInType(type));
    if (psiClass != null) {
      return psiClass.getQualifiedName();
    }

    return type != null ? type.getCanonicalText() : null;
  }

  @Nullable
  public static String getAccessibleReturnType(@Nullable PsiClass psiClass) {
    psiClass = nearestAccessedClass(psiClass);
    return psiClass == null ? null : psiClass.getQualifiedName();
  }

  @NotNull
  @Contract(pure = true)
  public static String classForName(@NotNull String typeName) {
    return TypeConversionUtil.isPrimitive(typeName) ? typeName + ".class" : "java.lang.Class.forName(\"" + typeName + "\")";
  }
}
