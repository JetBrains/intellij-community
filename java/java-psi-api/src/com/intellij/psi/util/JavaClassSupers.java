// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class JavaClassSupers {

  public static JavaClassSupers getInstance() {
    return ApplicationManager.getApplication().getService(JavaClassSupers.class);
  }

  /**
   * Calculates substitutor that binds type parameters in {@code superClass} with
   * values that they have in {@code derivedClass}, given that type parameters in
   * {@code derivedClass} are bound by {@code derivedSubstitutor}.
   *
   * @return substitutor or {@code null}, if {@code derivedClass} doesn't inherit {@code superClass}
   * @see PsiClass#isInheritor(PsiClass, boolean)
   * @see InheritanceUtil#isInheritorOrSelf(PsiClass, PsiClass, boolean)
   */
  @Nullable
  public abstract PsiSubstitutor getSuperClassSubstitutor(@NotNull PsiClass superClass,
                                                          @NotNull PsiClass derivedClass,
                                                          @NotNull GlobalSearchScope resolveScope,
                                                          @NotNull PsiSubstitutor derivedSubstitutor);

  /**
   * Called internally when it's expected that derivedClass extends superClass, but no super class substitutor can be found for them
   */
  public abstract void reportHierarchyInconsistency(@NotNull PsiClass superClass, @NotNull PsiClass derivedClass);


}
