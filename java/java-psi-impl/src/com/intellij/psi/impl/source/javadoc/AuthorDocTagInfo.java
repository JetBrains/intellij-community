// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;

class AuthorDocTagInfo extends SimpleDocTagInfo {
  AuthorDocTagInfo() {
    super("author", LanguageLevel.JDK_1_3, false, PsiClass.class, PsiPackage.class, PsiMethod.class, PsiJavaModule.class);
  }

  @Override
  public boolean isValidInContext(PsiElement element) {
    if (element instanceof PsiMethod && !element.isPhysical()) {
      return false;
    }
    else {
      return super.isValidInContext(element);
    }
  }
}