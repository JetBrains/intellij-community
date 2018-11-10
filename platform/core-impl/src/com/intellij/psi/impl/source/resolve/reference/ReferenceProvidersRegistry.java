/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.lang.Language;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
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
    return getInstance().doGetReferencesFromProviders(context, hints);
  }

  @NotNull
  protected abstract PsiReference[] doGetReferencesFromProviders(@NotNull PsiElement context, @NotNull PsiReferenceService.Hints hints);
}
