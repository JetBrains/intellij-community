// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.lang.jvm.JvmLanguageDumbAware;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public final class JavaLanguageDumbAware implements JvmLanguageDumbAware {
  @Override
  public boolean supportDumbMode(@NotNull PsiElement psiElement) {
    return psiElement.getLanguage() == JavaLanguage.INSTANCE;
  }
}
