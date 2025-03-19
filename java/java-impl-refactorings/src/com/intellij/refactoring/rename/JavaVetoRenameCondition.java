// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.util.FileTypeUtils;

public final class JavaVetoRenameCondition implements Condition<PsiElement> {
  @Override
  public boolean value(PsiElement element) {
    if (element instanceof LightMethod) {
      PsiClass containingClass = ((LightMethod)element).getContainingClass();
      if (containingClass.isEnum()) return true;
    }
    if (element instanceof PsiImplicitClass) {
      return true;
    }

    if (element instanceof PsiReceiverParameter) {
      return true;
    }

    return element instanceof PsiJavaFile &&
           !FileTypeUtils.isInServerPageFile(element) &&
           !JavaProjectRootsUtil.isOutsideJavaSourceRoot((PsiFile)element) &&
           ((PsiJavaFile) element).getClasses().length > 0;
  }
}
