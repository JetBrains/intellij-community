// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.util.CachedValuesFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PsiCachedValuesFactory implements CachedValuesFactory {

  private static final boolean preferHardRefsForPsiCachedValue =
    !"false".equalsIgnoreCase(System.getProperty("ide.prefer.hard.refs.psi.cached.value"));

  private final PsiManager myManager;

  public PsiCachedValuesFactory(@NotNull Project project) {
    myManager = PsiManager.getInstance(project);
  }

  @Override
  public @NotNull <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider, boolean trackValue) {
    if (trackValue) {
      return new PsiCachedValueImpl.SoftTracked<>(myManager, provider);
    }

    return new PsiCachedValueImpl.Soft<>(myManager, provider);
  }

  @Override
  public @NotNull <T> CachedValue<T> createCachedValue(@NotNull UserDataHolder userDataHolder,
                                                       @NotNull CachedValueProvider<T> provider,
                                                       boolean trackValue) {
    if (preferHardRefs(userDataHolder)) {

      return trackValue ?
             new PsiCachedValueImpl.DirectTracked<>(myManager, provider) :
             new PsiCachedValueImpl.Direct<>(myManager, provider);
    }

    return createCachedValue(provider, trackValue);
  }

  @Override
  public @NotNull <T, P> ParameterizedCachedValue<T, P> createParameterizedCachedValue(@NotNull ParameterizedCachedValueProvider<T, P> provider,
                                                                                       boolean trackValue) {
    return trackValue ?
           new PsiParameterizedCachedValue.SoftTracked<>(myManager, provider) :
           new PsiParameterizedCachedValue.Soft<>(myManager, provider);
  }

  @Override
  public @NotNull <T, P> ParameterizedCachedValue<T, P> createParameterizedCachedValue(@NotNull UserDataHolder userDataHolder,
                                                                                       @NotNull ParameterizedCachedValueProvider<T, P> provider,
                                                                                       boolean trackValue) {
    if (preferHardRefs(userDataHolder)) {
      return trackValue ?
             new PsiParameterizedCachedValue.DirectTracked<>(myManager, provider) :
             new PsiParameterizedCachedValue.Direct<>(myManager, provider);
    }

    return createParameterizedCachedValue(provider, trackValue);
  }

  private static boolean preferHardRefs(@NotNull UserDataHolder userDataHolder) {
    return preferHardRefsForPsiCachedValue
           && userDataHolder instanceof PsiElement
           && !(userDataHolder instanceof StubBasedPsiElement) // StubBasedPsiElement cache may outlive the loaded content of a file
           && !(userDataHolder instanceof PsiFile);
  }

}
