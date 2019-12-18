// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecordComponent;
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
}
