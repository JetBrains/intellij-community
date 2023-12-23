// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.util.CachedValuesFactory;
import org.jetbrains.annotations.NotNull;

public final class PsiCachedValuesFactory implements CachedValuesFactory {

  private static final boolean preferHardRefsForPsiCachedValue =
    !"false".equalsIgnoreCase(System.getProperty("ide.prefer.hard.refs.psi.cached.value"));

  private final PsiManager myManager;

  public PsiCachedValuesFactory(@NotNull Project project) {
    myManager = PsiManager.getInstance(project);
  }

  @Override
  public @NotNull <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider, boolean trackValue) {
    return new PsiCachedValueImpl.Soft<>(myManager, provider, trackValue);
  }

  @Override
  public @NotNull <T> CachedValue<T> createCachedValue(@NotNull UserDataHolder userDataHolder,
                                                       @NotNull CachedValueProvider<T> provider,
                                                       boolean trackValue) {
    if (preferHardRefsForPsiCachedValue
        && userDataHolder instanceof PsiElement
        && !(userDataHolder instanceof PsiFile)) { // PsiFile cache may outlive the loaded content of file
      return new PsiCachedValueImpl.Direct<>(myManager, provider, trackValue);
    }

    return createCachedValue(provider, trackValue);
  }

  @Override
  public @NotNull <T, P> ParameterizedCachedValue<T, P> createParameterizedCachedValue(@NotNull ParameterizedCachedValueProvider<T, P> provider,
                                                                                       boolean trackValue) {
    return new PsiParameterizedCachedValue.Soft<>(myManager, provider, trackValue);
  }

  @Override
  public @NotNull <T, P> ParameterizedCachedValue<T, P> createParameterizedCachedValue(@NotNull UserDataHolder userDataHolder,
                                                                                       @NotNull ParameterizedCachedValueProvider<T, P> provider,
                                                                                       boolean trackValue) {
    if (preferHardRefsForPsiCachedValue
        && userDataHolder instanceof PsiElement
        && !(userDataHolder instanceof PsiFile)) { // PsiFile cache may outlive the loaded content of file
      return new PsiParameterizedCachedValue.Direct<>(myManager, provider, trackValue);
    }

    return createParameterizedCachedValue(provider, trackValue);
  }
}
