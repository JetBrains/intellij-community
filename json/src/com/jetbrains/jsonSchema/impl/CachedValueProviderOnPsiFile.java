// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CachedValueProviderOnPsiFile<T> implements CachedValueProvider<T> {
  private final PsiFile myPsiFile;

  public CachedValueProviderOnPsiFile(@NotNull PsiFile psiFile) {
    myPsiFile = psiFile;
  }

  @Nullable
  @Override
  public Result<T> compute() {
    return CachedValueProvider.Result.create(evaluate(myPsiFile), myPsiFile);
  }

  @Nullable
  public abstract T evaluate(@NotNull PsiFile psiFile);

  @Nullable
  public static <T> T getOrCompute(@NotNull PsiFile psiFile, @NotNull Function<PsiFile, T> eval, @NotNull Key<CachedValue<T>> key) {
    final CachedValueProvider<T> provider = new CachedValueProviderOnPsiFile<T>(psiFile) {
      @Override
      @Nullable
      public T evaluate(@NotNull PsiFile psiFile) {
        return eval.fun(psiFile);
      }
    };
    return ReadAction.compute(() -> CachedValuesManager.getCachedValue(psiFile, key, provider));
  }
}
