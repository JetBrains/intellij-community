// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;


public final class JavaNonCodeSearchElementDescriptionProvider implements ElementDescriptionProvider {
  @Override
  public String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location) {
    if (location instanceof NonCodeSearchDescriptionLocation ncdLocation) {
      switch (element) {
        case PsiPackage pkg -> {
          return ncdLocation.isNonJava() ? pkg.getQualifiedName() : StringUtil.notNullize(pkg.getName());
        }
        case PsiClass cls -> {
          return ncdLocation.isNonJava() ? cls.getQualifiedName() : cls.getName();
        }
        case PsiMember member -> {
          String name = member.getName();
          if (name == null) {
            return null;
          }
          if (ncdLocation.isNonJava()) {
            PsiClass containingClass = member.getContainingClass();
            if (containingClass == null || containingClass.getQualifiedName() == null) {
              return null;
            }
            return containingClass.getQualifiedName() + "." + name;
          }
          return name;
        }
        default -> {
        }
      }
    }
    return null;
  }
}
