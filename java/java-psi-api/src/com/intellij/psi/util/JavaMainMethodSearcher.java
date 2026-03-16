// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class JavaMainMethodSearcher extends JvmMainMethodSearcher {
  public static final JvmMainMethodSearcher INSTANCE = new JavaMainMethodSearcher();

  private JavaMainMethodSearcher() { }

  @Override
  public boolean instanceMainMethodsEnabled(@NotNull PsiElement psiElement) {
    return PsiUtil.isAvailable(JavaFeature.INSTANCE_MAIN_METHOD, psiElement) && psiElement.getLanguage() instanceof JavaLanguage;
  }

  @Override
  protected boolean inheritedStaticMainEnabled(@NotNull PsiElement psiElement) {
    return PsiUtil.isAvailable(JavaFeature.INHERITED_STATIC_MAIN_METHOD, psiElement);
  }
}
