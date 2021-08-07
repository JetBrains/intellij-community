// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;


public class JavaNonCodeSearchElementDescriptionProvider implements ElementDescriptionProvider {
  @Override
  public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
    if (!(location instanceof NonCodeSearchDescriptionLocation)) return null;
    NonCodeSearchDescriptionLocation ncdLocation = (NonCodeSearchDescriptionLocation) location;
    if (element instanceof PsiPackage) {
      return ncdLocation.isNonJava() ? ((PsiPackage)element).getQualifiedName() : StringUtil.notNullize(((PsiPackage)element).getName());
    }
    if (element instanceof PsiClass) {
      return ncdLocation.isNonJava() ? ((PsiClass)element).getQualifiedName() : ((PsiClass)element).getName();
    }
    if (element instanceof PsiMember) {
      PsiMember member = (PsiMember)element;
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
