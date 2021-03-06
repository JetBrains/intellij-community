// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public class JavaOverrideMethodsProcessor implements OverrideMethodsProcessor {
  @Override
  public boolean removeOverrideAttribute(@NotNull PsiElement element) {
    if (element instanceof PsiMethod) {
      PsiAnnotation annotation = AnnotationUtil.findAnnotation((PsiMethod)element, true, Override.class.getName());
      if (annotation != null) {
        annotation.delete();
        return true;
      }
    }
    return false;
  }
}

