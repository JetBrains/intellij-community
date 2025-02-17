// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods related to Java enums
 */
public final class JavaPsiEnumUtil {
  /**
   * @param field field to check
   * @param enumClass an enum class returned from {@link #getEnumClassForExpressionInInitializer(PsiExpression)}
   * @return true if the given field cannot be referenced in constructors or instance initializers of the given enum class.
   */
  public static boolean isRestrictedStaticEnumField(@NotNull PsiField field, @NotNull PsiClass enumClass) {
    if (!field.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (field.getContainingClass() != enumClass) return false;

    if (!JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_6)) {
      PsiType type = field.getType();
      if (type instanceof PsiClassType classType && classType.resolve() == enumClass) return false;
    }

    return !PsiUtil.isCompileTimeConstant(field);
  }

  /**
   * @param expr expression to analyze
   * @return enum class, whose non-constant static fields cannot be used at a given place,
   * null if there's no such restriction 
   */
  public static @Nullable PsiClass getEnumClassForExpressionInInitializer(@NotNull PsiExpression expr) {
    if (PsiImplUtil.getSwitchLabel(expr) != null) return null;
    PsiMember constructorOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expr);
    if (constructorOrInitializer == null || constructorOrInitializer.hasModifierProperty(PsiModifier.STATIC)) return null;
    PsiClass enumClass = constructorOrInitializer instanceof PsiEnumConstantInitializer initializer
                         ? initializer
                         : constructorOrInitializer.getContainingClass();
    if (enumClass instanceof PsiEnumConstantInitializer) {
      enumClass = enumClass.getSuperClass();
    }
    return enumClass != null && enumClass.isEnum() ? enumClass : null;
  }
}
