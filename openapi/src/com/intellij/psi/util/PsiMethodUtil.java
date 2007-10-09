/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
public class PsiMethodUtil {
  public static Condition<PsiClass> MAIN_CLASS = new Condition<PsiClass>() {
    public boolean value(final PsiClass psiClass) {
      if (psiClass instanceof PsiAnonymousClass) return false;
      if (psiClass.isInterface()) return false;
      return psiClass.getContainingClass() == null || psiClass.hasModifierProperty(PsiModifier.STATIC);
    }
  };

  private PsiMethodUtil() {
  }

  @Nullable
  public static PsiMethod findMainMethod(final PsiClass aClass) {
    final PsiMethod[] mainMethods = aClass.findMethodsByName("main", false);
    return findMainMethod(mainMethods);
  }

  @Nullable
  public static PsiMethod findMainMethod(final PsiMethod[] mainMethods) {
    for (final PsiMethod mainMethod : mainMethods) {
      if (isMainMethod(mainMethod)) return mainMethod;
    }
    return null;
  }

  public static boolean isMainMethod(final PsiMethod method) {
    if (method == null) return false;
    if (PsiType.VOID != method.getReturnType()) return false;
    if (!method.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    final PsiType type = parameters[0].getType();
    if (!(type instanceof PsiArrayType)) return false;
    final PsiType componentType = ((PsiArrayType)type).getComponentType();
    return componentType.equalsToText("java.lang.String");
  }

  public static boolean hasMainMethod(final PsiClass psiClass) {
    return findMainMethod(psiClass.findMethodsByName("main", true)) != null;
  }

  @Nullable
  public static PsiMethod findMainInClass(final PsiClass aClass) {
    if (!ConfigurationUtil.MAIN_CLASS.value(aClass)) return null;
    return findMainMethod(aClass);
  }
}
