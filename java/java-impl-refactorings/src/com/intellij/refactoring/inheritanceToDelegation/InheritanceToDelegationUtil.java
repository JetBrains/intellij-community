// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;

/**
 * @author dsl
 */
public final class InheritanceToDelegationUtil {
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
        if (!CommonClassNames.JAVA_LANG_OBJECT.equals(qName)) return true;
      }
    }
    return false;
  }
}
