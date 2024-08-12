// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.MixinEP;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeclarationRangeUtil {
  private static final ExtensionPointName<MixinEP<DeclarationRangeHandler<PsiElement>>> EP_NAME = new ExtensionPointName<>("com.intellij.declarationRangeHandler");

  public static @NotNull TextRange getDeclarationRange(@NotNull PsiElement container) {
    TextRange textRange = getPossibleDeclarationAtRange(container);
    assert textRange != null : "Declaration range is invalid for " + container.getClass();
    return textRange;
  }

  public static @Nullable TextRange getPossibleDeclarationAtRange(@NotNull PsiElement container) {
    for (MixinEP<DeclarationRangeHandler<PsiElement>> ep: EP_NAME.getIterable()) {
      if (ep.getKey().isInstance(container)) {
        return ep.getInstance().getDeclarationRange(container);
      }
    }
    return null;
  }
}