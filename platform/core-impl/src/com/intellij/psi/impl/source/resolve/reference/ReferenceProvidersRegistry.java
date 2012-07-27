/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:13:45
 * To change this template use Options | File Templates.
 */
public abstract class ReferenceProvidersRegistry {
  public final static PsiReferenceProvider NULL_REFERENCE_PROVIDER = new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        return PsiReference.EMPTY_ARRAY;
      }
    };

  public static ReferenceProvidersRegistry getInstance() {
    return ServiceManager.getService(ReferenceProvidersRegistry.class);
  }

  public abstract PsiReferenceRegistrar getRegistrar(Language language);

  /**
   * @see #getReferencesFromProviders(com.intellij.psi.PsiElement)
   */
  @Deprecated
  public static PsiReference[] getReferencesFromProviders(PsiElement context, @NotNull Class clazz) {
    return getReferencesFromProviders(context, PsiReferenceService.Hints.NO_HINTS);
  }

  public static PsiReference[] getReferencesFromProviders(PsiElement context) {
    return getReferencesFromProviders(context, PsiReferenceService.Hints.NO_HINTS);
  }

  public static PsiReference[] getReferencesFromProviders(PsiElement context, @NotNull PsiReferenceService.Hints hints) {
    ProgressIndicatorProvider.checkCanceled();
    assert context.isValid() : "Invalid context: " + context;

    ReferenceProvidersRegistry registry = getInstance();
    return registry.doGetReferencesFromProviders(context, hints);
  }

  protected abstract PsiReference[] doGetReferencesFromProviders(PsiElement context, PsiReferenceService.Hints hints);
}
