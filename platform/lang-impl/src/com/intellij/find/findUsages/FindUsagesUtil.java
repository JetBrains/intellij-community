// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FindUsagesUtil {
  private FindUsagesUtil() { }

  public static boolean isSearchForTextOccurrencesAvailable(@NotNull PsiElement element, boolean isSingleFile, @Nullable FindUsagesHandlerBase handler) {
    return !isSingleFile && handler != null && FindUsagesHelper.isSearchForTextOccurrencesAvailable(handler, element, false);
  }
}
