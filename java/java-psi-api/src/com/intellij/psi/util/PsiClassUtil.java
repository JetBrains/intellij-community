/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;

/**
 * @author mike
 */
public class PsiClassUtil {
  private PsiClassUtil() {
  }

  public static boolean isRunnableClass(final PsiClass aClass, final boolean mustBePublic) {
    return isRunnableClass(aClass, mustBePublic, true);
  }
  public static boolean isRunnableClass(final PsiClass aClass, final boolean mustBePublic, boolean mustNotBeAbstract) {
    if (aClass instanceof PsiAnonymousClass) return false;
    if (aClass.isInterface()) return false;
    if (mustBePublic && !aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
      if (mustNotBeAbstract || !aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return false;
      }
    }
    if (aClass.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    if (mustNotBeAbstract && aClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    return aClass.getContainingClass() == null || aClass.hasModifierProperty(PsiModifier.STATIC);
  }
}
