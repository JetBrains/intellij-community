// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiReceiverParameter;
import com.intellij.psi.impl.light.LightMethod;

public final class JavaVetoRenameCondition implements Condition<PsiElement> {
  @Override
  public boolean value(PsiElement element) {
    if (element instanceof LightMethod method && method.getContainingClass().isEnum()) return true;
    if (element instanceof PsiImplicitClass) return true;
    if (element instanceof PsiReceiverParameter) return true;

    return false;
  }
}
