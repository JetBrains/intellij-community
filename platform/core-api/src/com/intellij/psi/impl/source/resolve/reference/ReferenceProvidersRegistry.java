// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.codeInsight.highlighting.PassRunningAssert;
import com.intellij.lang.Language;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public abstract class ReferenceProvidersRegistry {
  private static final PassRunningAssert CONTRIBUTING_REFERENCES = new PassRunningAssert(
    "the expensive method should not be called during the references contributing");

  public static final PsiReferenceProvider NULL_REFERENCE_PROVIDER = new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        return PsiReference.EMPTY_ARRAY;
      }
    };

  public static ReferenceProvidersRegistry getInstance() {
    return ApplicationManager.getApplication().getService(ReferenceProvidersRegistry.class);
  }

  public abstract @NotNull PsiReferenceRegistrar getRegistrar(@NotNull Language language);

  public static PsiReference @NotNull [] getReferencesFromProviders(@NotNull PsiElement context) {
    return getReferencesFromProviders(context, PsiReferenceService.Hints.NO_HINTS);
  }

  public static PsiReference @NotNull [] getReferencesFromProviders(@NotNull PsiElement context, @NotNull PsiReferenceService.Hints hints) {
    ProgressIndicatorProvider.checkCanceled();

    if (hints == PsiReferenceService.Hints.NO_HINTS) {
      return CachedValuesManager.getCachedValue(context, () -> {
        try (AccessToken ignored = CONTRIBUTING_REFERENCES.runPass()) {
          return Result.create(getInstance().doGetReferencesFromProviders(context, PsiReferenceService.Hints.NO_HINTS),
                               PsiModificationTracker.MODIFICATION_COUNT);
        }
      }).clone();
    }

    try (AccessToken ignored = CONTRIBUTING_REFERENCES.runPass()) {
      return getInstance().doGetReferencesFromProviders(context, hints);
    }
  }

  public static void assertNotContributingReferences() {
    CONTRIBUTING_REFERENCES.assertPassNotRunning();
  }

  /**
   * @deprecated suppressing this assert could cause a performance flaw
   */
  @ApiStatus.Internal
  @Deprecated
  public static AccessToken suppressAssertNotContributingReferences() {
    return CONTRIBUTING_REFERENCES.suppressAssertInPass();
  }

  @ApiStatus.Internal
  public abstract void unloadProvidersFor(@NotNull Language language);

  protected abstract PsiReference @NotNull [] doGetReferencesFromProviders(@NotNull PsiElement context, @NotNull PsiReferenceService.Hints hints);
}
