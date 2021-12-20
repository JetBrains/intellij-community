// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class InvalidPsiAutoFormatRestriction implements LanguageFormattingRestriction {
  @Override
  public boolean isFormatterAllowed(@NotNull PsiElement context) {
    return true;
  }

  @Override
  public boolean isAutoFormatAllowed(@NotNull PsiElement context) {
    return context.isValid() && containsValidPsi(context);
  }


  private static boolean containsValidPsi(@NotNull PsiElement context) {
    return !PsiTreeUtil.hasErrorElements(context);
  }
}
