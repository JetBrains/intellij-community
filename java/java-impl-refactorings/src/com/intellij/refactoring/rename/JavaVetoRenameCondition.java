// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReceiverParameter;
import com.intellij.psi.SyntheticElement;

public final class JavaVetoRenameCondition implements Condition<PsiElement> {
  @Override
  public boolean value(PsiElement element) {
    if (element instanceof PsiMethod method && method instanceof SyntheticElement) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null && aClass.isEnum()) return true;
    } 
    if (element instanceof PsiImplicitClass) return true;
    if (element instanceof PsiReceiverParameter) return true;

    return false;
  }
}
