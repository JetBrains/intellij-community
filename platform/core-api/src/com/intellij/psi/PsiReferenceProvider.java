// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Allows injecting additional references into an element that supports reference contributors.
 * Register it via {@link PsiReferenceContributor} or {@link PsiReferenceProviderBean} extension points.
 * <p>
 * Note that, if you're implementing a custom language, it won't by default support references registered through PsiReferenceContributor.
 * If you want to support that, you need to call
 * {@link com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry#getReferencesFromProviders(PsiElement)} from your implementation
 * of PsiElement.getReferences().
 *
 * @author ik
 */
public abstract class PsiReferenceProvider {

  public abstract PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, final @NotNull ProcessingContext context);

  /**
   * Check (preferably in a lightweight way) if this reference provider may return references at all, when invoked on a given PSI element
   * with given hints (offset, target). If {@code false} is returned, then neither the provider itself,
   * nor the associated {@link com.intellij.patterns.ElementPattern} is invoked. This can be used to speed up usage search
   * if, e.g. the references returned by this provider would never resolve to a specified target.
   * Note that for the hints to be passed correctly, the {@code element} should implement {@link ContributedReferenceHost}
   * or {@link HintedReferenceHost}.
   * @see #acceptsTarget
   */
  public boolean acceptsHints(final @NotNull PsiElement element, @NotNull PsiReferenceService.Hints hints) {
    final PsiElement target = hints.target;
    return target == null || acceptsTarget(target);
  }

  /**
   * A specialization of {@link #acceptsHints} that checks for target element only.
   */
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return true;
  }
}
