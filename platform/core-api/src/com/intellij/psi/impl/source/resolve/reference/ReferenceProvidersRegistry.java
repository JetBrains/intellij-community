// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.lang.Language;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public abstract class ReferenceProvidersRegistry {
  public static final PsiReferenceProvider NULL_REFERENCE_PROVIDER = new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        return PsiReference.EMPTY_ARRAY;
      }
    };

  public static ReferenceProvidersRegistry getInstance() {
    return ServiceManager.getService(ReferenceProvidersRegistry.class);
  }

  @NotNull
  public abstract PsiReferenceRegistrar getRegistrar(@NotNull Language language);

  @NotNull
  public static PsiReference[] getReferencesFromProviders(@NotNull PsiElement context) {
    return getReferencesFromProviders(context, PsiReferenceService.Hints.NO_HINTS);
  }

  @NotNull
  public static PsiReference[] getReferencesFromProviders(@NotNull PsiElement context, @NotNull PsiReferenceService.Hints hints) {
    ProgressIndicatorProvider.checkCanceled();

    if (hints == PsiReferenceService.Hints.NO_HINTS) {
      return CachedValuesManager.getCachedValue(context, () ->
        CachedValueProvider.Result.create(getInstance().doGetReferencesFromProviders(context, PsiReferenceService.Hints.NO_HINTS),
                                          PsiModificationTracker.MODIFICATION_COUNT)).clone();
    }

    return getInstance().doGetReferencesFromProviders(context, hints);
  }

  @ApiStatus.Internal
  public abstract void unloadProvidersFor(@NotNull Language language);

  @NotNull
  protected abstract PsiReference[] doGetReferencesFromProviders(@NotNull PsiElement context, @NotNull PsiReferenceService.Hints hints);
}
