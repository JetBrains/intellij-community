// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.filters.classes;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

public class NoFinalLibraryClassesFilter implements ElementFilter {
  @Override
  public boolean isAcceptable(Object element, @Nullable PsiElement context) {
    // Do not suggest final/sealed library classes
    return !(element instanceof PsiClass) ||
           !(element instanceof PsiCompiledElement) ||
           !((PsiClass)element).hasModifierProperty(PsiModifier.FINAL) &&
           !((PsiClass)element).hasModifierProperty(PsiModifier.SEALED);
  }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return ReflectionUtil.isAssignable(PsiClass.class, hintClass);
  }
}
