// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.util.PsiUtil;

final class ServiceReferenceTagInfo extends ClassReferenceTagInfo {
  ServiceReferenceTagInfo(String name) {
    super(name);
  }

  @Override
  public boolean isValidInContext(PsiElement element) {
    return element instanceof PsiJavaModule && PsiUtil.getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_9);
  }
}