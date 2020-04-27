// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;

class ReturnDocTagInfo implements JavadocTagInfo {
  @Override
  public String getName() {
    return "return";
  }

  @Override
  public boolean isInline() {
    return false;
  }

  @Override
  public boolean isValidInContext(PsiElement element) {
    if (!(element instanceof PsiMethod)) return false;
    PsiMethod method = (PsiMethod)element;
    final PsiType type = method.getReturnType();
    if (type == null) return false;
    return !PsiType.VOID.equals(type);
  }

  @Override
  public String checkTagValue(PsiDocTagValue value) {
    return null;
  }

  @Override
  public PsiReference getReference(PsiDocTagValue value) {
    return null;
  }
}