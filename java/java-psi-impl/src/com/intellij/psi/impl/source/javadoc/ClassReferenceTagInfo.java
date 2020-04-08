// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;

abstract class ClassReferenceTagInfo implements JavadocTagInfo {
  private final String myName;

  ClassReferenceTagInfo(String name) {
    myName = name;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isInline() {
    return false;
  }

  @Override
  public String checkTagValue(PsiDocTagValue value) {
    PsiElement refHolder = value != null ? value.getFirstChild() : null;
    if (refHolder == null) {
      return JavaPsiBundle.message("javadoc.ref.tag.class.ref.expected");
    }

    PsiElement refElement = refHolder.getFirstChild();
    if (!(refElement instanceof PsiJavaCodeReferenceElement)) {
      return JavaPsiBundle.message("javadoc.exception.tag.wrong.tag.value");
    }

    return null;
  }

  @Override
  public PsiReference getReference(PsiDocTagValue value) {
    return null;
  }
}