// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

class SimpleDocTagInfo implements JavadocTagInfo {
  private final String myName;
  private final Class[] myContexts;
  private final boolean myInline;
  private final LanguageLevel myLanguageLevel;

  SimpleDocTagInfo(@NotNull String name, @NotNull LanguageLevel level, boolean isInline, Class @NotNull ... contexts) {
    myName = name;
    myContexts = contexts;
    myInline = isInline;
    myLanguageLevel = level;
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
    if (element != null && PsiUtil.getLanguageLevel(element).compareTo(myLanguageLevel) < 0) {
      return false;
    }
    for (Class context : myContexts) {
      if (context.isInstance(element)) return true;
    }
    return false;
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