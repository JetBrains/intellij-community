// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to inject additional references into an element that supports reference contributors.
 * Register it via {@link PsiReferenceContributor} or {@link PsiReferenceProviderBean#EP_NAME}
 *
 * Note that, if you're implementing a custom language, it won't by default support references registered through PsiReferenceContributor.
 * If you want to support that, you need to call
 * {@link com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry#getReferencesFromProviders(PsiElement)} from your implementation
 * of PsiElement.getReferences().
 *
 * @author ik
 */
public abstract class PsiReferenceProvider {

  @NotNull
  public abstract PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context);

  public boolean acceptsHints(@NotNull final PsiElement element, @NotNull PsiReferenceService.Hints hints) {
    final PsiElement target = hints.target;
    return target == null || acceptsTarget(target);
  }

  public boolean acceptsTarget(@NotNull PsiElement target) {
    return true;
  }
}
