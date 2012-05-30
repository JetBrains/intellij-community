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
package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.psi.*;

/**
 * @author dsl
 */
public class InheritanceToDelegationUtil {
  private InheritanceToDelegationUtil() {
  }

  public static boolean isInnerClassNeeded(PsiClass aClass, PsiClass baseClass) {
    if(baseClass.isInterface()) return true;
    if(baseClass.hasModifierProperty(PsiModifier.ABSTRACT)) return true;
    PsiMethod[] methods = aClass.getMethods();

    for (PsiMethod method : methods) {
      if (method.isConstructor() || method.hasModifierProperty(PsiModifier.PRIVATE)) continue;
      PsiMethod baseMethod = baseClass.findMethodBySignature(method, true);
      if (baseMethod != null) {
        PsiClass containingClass = baseMethod.getContainingClass();
        String qName = containingClass.getQualifiedName();
        if (qName == null || !CommonClassNames.JAVA_LANG_OBJECT.equals(qName)) return true;
      }
    }
    return false;
  }
}
