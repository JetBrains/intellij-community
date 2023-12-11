// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;


public final class JavaNonCodeSearchElementDescriptionProvider implements ElementDescriptionProvider {
  @Override
  public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
    if (!(location instanceof NonCodeSearchDescriptionLocation ncdLocation)) return null;
    if (element instanceof PsiPackage pkg) {
      return ncdLocation.isNonJava() ? pkg.getQualifiedName() : StringUtil.notNullize(pkg.getName());
    }
    if (element instanceof PsiClass cls) {
      return ncdLocation.isNonJava() ? cls.getQualifiedName() : cls.getName();
    }
    if (element instanceof PsiMember member) {
      String name = member.getName();
      if (name == null) return null;
      if (ncdLocation.isNonJava()) {
        PsiClass containingClass = member.getContainingClass();
        if (containingClass == null || containingClass.getQualifiedName() == null) return null;
        return containingClass.getQualifiedName() + "." + name;
      }
      return name;
    }
    return null;
  }
}
