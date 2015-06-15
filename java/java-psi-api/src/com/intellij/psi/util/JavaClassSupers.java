/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.util;

import com.intellij.openapi.components.ServiceManager;
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
    return ServiceManager.getService(JavaClassSupers.class);
  }

  /**
   * Calculates substitutor that binds type parameters in <code>superClass</code> with
   * values that they have in <code>derivedClass</code>, given that type parameters in
   * <code>derivedClass</code> are bound by <code>derivedSubstitutor</code>.
   *
   * @return substitutor or <code>null</code>, if <code>derivedClass</code> doesn't inherit <code>superClass</code>
   * @see PsiClass#isInheritor(PsiClass, boolean)
   * @see InheritanceUtil#isInheritorOrSelf(PsiClass, PsiClass, boolean)
   */
  @Nullable
  public abstract PsiSubstitutor getSuperClassSubstitutor(@NotNull PsiClass superClass,
                                                          @NotNull PsiClass derivedClass,
                                                          @NotNull GlobalSearchScope resolveScope,
                                                          @NotNull PsiSubstitutor derivedSubstitutor);


}
