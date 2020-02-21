// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiUtil;

class SeeDocTagInfo implements JavadocTagInfo {
  private static final String LINKPLAIN_TAG = "linkplain";

  private final String myName;
  private final boolean myInline;

  SeeDocTagInfo(String name, boolean isInline) {
    myName = name;
    myInline = isInline;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isInline() {
    return myInline;
  }


  @Override
  public boolean isValidInContext(PsiElement element) {
    if (myInline && myName.equals(LINKPLAIN_TAG) && element != null) {
      return PsiUtil.getLanguageLevel(element).compareTo(LanguageLevel.JDK_1_4) >= 0;
    }

    return true;
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