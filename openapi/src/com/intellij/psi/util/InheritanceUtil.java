/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public class InheritanceUtil {
  /**
   * @deprecated Use {@link PsiClass#isInheritor(com.intellij.psi.PsiClass, boolean)} instead.
   */
  public static boolean isInheritor(@NotNull PsiClass candidateClass, @NotNull PsiClass baseClass, boolean checkDeep) {
    return candidateClass.isInheritor(baseClass, checkDeep);
  }

  /**
   * @return true if aClass is the baseClass or baseClass inheritor
   */
  public static boolean isInheritorOrSelf(@Nullable PsiClass aClass, @Nullable PsiClass baseClass, boolean checkDeep) { //TODO: remove this method!!
    if (aClass == null || baseClass == null) return false;
    PsiManager manager = aClass.getManager();
    return manager.areElementsEquivalent(baseClass, aClass) || aClass.isInheritor(baseClass, checkDeep);
  }

  /**
   * @return true if aClass is the baseClass or baseClass inheritor
   */
  public static boolean isCorrectDescendant(@Nullable PsiClass aClass, @Nullable PsiClass baseClass, boolean checkDeep) {
    return isInheritorOrSelf(aClass, baseClass, checkDeep);
  }
}
