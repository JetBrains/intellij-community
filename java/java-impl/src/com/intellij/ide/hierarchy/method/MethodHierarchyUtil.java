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
package com.intellij.ide.hierarchy.method;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.MethodSignatureUtil;

final class MethodHierarchyUtil {
  public static PsiMethod findBaseMethodInClass(final PsiMethod baseMethod, final PsiClass aClass, final boolean checkBases) {
    if (baseMethod == null) return null; // base method is invalid
    if (cannotBeOverridding(baseMethod)) return null;
    /*if (!checkBases) return MethodSignatureUtil.findMethodBySignature(aClass, signature, false);*/
    return MethodSignatureUtil.findMethodBySuperMethod(aClass, baseMethod, checkBases);
    /*final MethodSignatureBackedByPsiMethod signature = SuperMethodsSearch.search(baseMethod, aClass, checkBases, false).findFirst();
    return signature == null ? null : signature.getMethod();*/
  }

  private static boolean cannotBeOverridding(final PsiMethod method) {
    final PsiClass parentClass = method.getContainingClass();
    return parentClass == null
           || method.isConstructor()
           || method.hasModifierProperty(PsiModifier.STATIC)
           || method.hasModifierProperty(PsiModifier.PRIVATE);
  }

}
