// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Gregory.Shrago
 */
public abstract class PsiReferenceService {

  public static PsiReferenceService getService() {
    return ApplicationManager.getApplication().getService(PsiReferenceService.class);
  }

  /**
   * By default, return the same as {@link PsiElement#getReferences()}.
   * For elements implementing {@link ContributedReferenceHost} also run
   * the reference providers registered in {@link PsiReferenceContributor}
   * extensions.
   * @param element PSI element to which the references will be bound
   * @param hints optional hints which are passed to {@link PsiReferenceProvider#acceptsHints(PsiElement, PsiReferenceService.Hints)} and
   * {@link PsiReferenceProvider#acceptsTarget(PsiElement)} before the {@link com.intellij.patterns.ElementPattern} is matched, for performing
   * fail-fast checks in case the pattern takes a long time to match.
   * @return the references
   */
  public abstract @NotNull List<PsiReference> getReferences(final @NotNull PsiElement element, final @NotNull Hints hints);

  public PsiReference @NotNull [] getContributedReferences(final @NotNull PsiElement element) {
    final List<PsiReference> list = getReferences(element, Hints.NO_HINTS);
    return list.toArray(PsiReference.EMPTY_ARRAY);
  }

  /**
   * Hints to be passed to PSI when searching for usages, allowing to avoid creating all references when none of them would be suitable.
   * @see PsiReferenceProvider#acceptsHints
   * @see ContributedReferenceHost
   * @see HintedReferenceHost
   */
  public static class Hints {
    public static final Hints NO_HINTS = new Hints();

    /**
     * Passed during highlighting to query only reference providers that may provide references that should be underlined.
     *
     * @see com.intellij.codeInsight.highlighting.HighlightedReference
     */
    public static final Hints HIGHLIGHTED_REFERENCES = new Hints();

    public final @Nullable PsiElement target;
    public final @Nullable Integer offsetInElement;

    public Hints() {
      target = null;
      offsetInElement = null;
    }

    public Hints(@Nullable PsiElement target, @Nullable Integer offsetInElement) {
      this.target = target;
      this.offsetInElement = offsetInElement;
    }
  }
}
