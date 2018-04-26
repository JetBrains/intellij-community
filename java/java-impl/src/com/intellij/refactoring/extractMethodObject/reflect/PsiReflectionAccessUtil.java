// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
class PsiReflectionAccessUtil {
  public static boolean isAccessibleMember(@NotNull PsiMember classMember) {
    return classMember.hasModifierProperty(PsiModifier.PUBLIC) && isAccessible(classMember.getContainingClass());
  }

  /**
   * Since we use new classloader for each "Evaluate expression" with compilation, the generated code has no
   * access to all members excluding public
   */
  @Contract("null -> false")
  public static boolean isAccessible(@Nullable PsiClass psiClass) {
    if (psiClass == null) return false;

    // currently, we use dummy psi class "_Array_" to represent arrays which is an inner of package-private _Dummy_ class.
    if (isArrayClass(psiClass)) return true;
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

  @Contract(value = "null -> true")
  public static boolean isQualifierAccessible(@Nullable PsiExpression qualifierExpression) {
    if (qualifierExpression == null) return true;
    PsiType type = qualifierExpression.getType();
    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    return psiClass == null || isAccessible(psiClass);
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

  @NotNull
  public static String getUniqueMethodName(@NotNull PsiClass psiClass, @NotNull String prefix) {
    if (!StringUtil.isJavaIdentifier(prefix)) throw new IllegalArgumentException("prefix must be a correct java identifier: " + prefix);
    int i = 1;
    String name;
    do {
      name = prefix + i;
      i++;
    }
    while (psiClass.findMethodsByName(name, false).length != 0);

    return name;
  }

  private static boolean isArrayClass(@NotNull PsiClass psiClass) {
    Project project = psiClass.getProject();
    PsiClass arrayClass = JavaPsiFacade.getElementFactory(project).getArrayClass(PsiUtil.getLanguageLevel(psiClass));
    return PsiEquivalenceUtil.areElementsEquivalent(psiClass, arrayClass);
  }
}
