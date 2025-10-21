// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.psiutils.JavaDeprecationUtils;
import org.jetbrains.annotations.NotNull;

public final class DeprecatedSkipper extends CompletionPreselectSkipper {

  @Override
  public boolean skipElement(@NotNull LookupElement element, @NotNull CompletionLocation location) {
    if (!isCompletionFromJavaFile(location)) return false;

    PsiElement e = element.getPsiElement();
    return e != null && JavaDeprecationUtils.isDeprecated(e, location.getCompletionParameters().getPosition());  }

  private static boolean isCompletionFromJavaFile(CompletionLocation location) {
    return location.getCompletionParameters().getOriginalFile().getLanguage() == JavaLanguage.INSTANCE;
  }
}
