// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods to support Java records
 */
public class JavaPsiRecordUtil {
  /**
   * @param accessor accessor method for record component 
   * @return a corresponding record component, or null if the supplied method is not an accessor for the record component.
   * Note that if accessor is not well-formed (e.g. has wrong return type), the corresponding record component will still be returned.
   */
  @Nullable
  public static PsiRecordComponent getRecordComponentForAccessor(@NotNull PsiMethod accessor) {
    PsiClass aClass = accessor.getContainingClass();
    if (aClass == null || !aClass.isRecord()) return null;
    if (!accessor.getParameterList().isEmpty()) return null;
    String name = accessor.getName();
    for (PsiRecordComponent c : aClass.getRecordComponents()) {
      if (name.equals(c.getName())) {
        return c;
      }
    }
    return null;
  }

  /**
   * @param method to check
   * @return true if given method is a canonical constructor for a record class
   */
  public static boolean isCanonicalConstructor(@NotNull PsiMethod method) {
    if (!method.isConstructor()) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null || !aClass.isRecord()) return false;
    PsiRecordComponent[] components = aClass.getRecordComponents();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (components.length != parameters.length) return false;
    for (int i = 0; i < parameters.length; i++) {
      PsiType componentType = components[i].getType();
      PsiType parameterType = parameters[i].getType();
      if (!TypeConversionUtil.erasure(componentType).equals(TypeConversionUtil.erasure(parameterType))) return false;
    }
    return true;
  }
}
