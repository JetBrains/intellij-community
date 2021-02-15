// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.util.CachedValuesFactory;
import org.jetbrains.annotations.NotNull;

public final class PsiCachedValuesFactory implements CachedValuesFactory {
  private final PsiManager myManager;

  public PsiCachedValuesFactory(@NotNull Project project) {
    myManager = PsiManager.getInstance(project);
  }

  @Override
  public @NotNull <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider, boolean trackValue) {
    return new PsiCachedValueImpl<>(myManager, provider, trackValue);
  }

  @Override
  public @NotNull <T, P> ParameterizedCachedValue<T, P> createParameterizedCachedValue(@NotNull ParameterizedCachedValueProvider<T, P> provider,
                                                                                       boolean trackValue) {
    return new PsiParameterizedCachedValue<>(myManager, provider, trackValue);
  }
}
