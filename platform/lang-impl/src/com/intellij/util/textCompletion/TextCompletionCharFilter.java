// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.textCompletion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TextCompletionCharFilter extends CharFilter {
  @Override
  public @Nullable Result acceptChar(char c, int prefixLength, @NotNull Lookup lookup) {
    if (!lookup.isCompletion()) return null;
    PsiFile file = lookup.getPsiFile();
    if (file == null) return null;
    TextCompletionProvider provider = TextCompletionUtil.getProvider(file);
    if (provider != null) return provider.acceptChar(c);
    return null;
  }
}
