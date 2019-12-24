// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Gregory.Shrago
 */
public abstract class PsiReferenceService {

  public static PsiReferenceService getService() {
    return ServiceManager.getService(PsiReferenceService.class);
  }

  /**
   * By default, return the same as {@link com.intellij.psi.PsiElement#getReferences()}.
   * For elements implementing {@link com.intellij.psi.ContributedReferenceHost} also run
   * the reference providers registered in {@link com.intellij.psi.PsiReferenceContributor}
   * extensions.
   * @param element PSI element to which the references will be bound
   * @param hints optional hints which are passed to {@link com.intellij.psi.PsiReferenceProvider#acceptsHints(PsiElement, com.intellij.psi.PsiReferenceService.Hints)} and
   * {@link com.intellij.psi.PsiReferenceProvider#acceptsTarget(PsiElement)} before the {@link com.intellij.patterns.ElementPattern} is matched, for performing
   * fail-fast checks in case the pattern takes long to match.
   * @return the references
   */
  @NotNull
  public abstract List<PsiReference> getReferences(@NotNull final PsiElement element, @NotNull final Hints hints);

  @NotNull
  public PsiReference[] getContributedReferences(@NotNull final PsiElement element) {
    final List<PsiReference> list = getReferences(element, Hints.NO_HINTS);
    return list.toArray(PsiReference.EMPTY_ARRAY);
  }


  public static class Hints {
    public static final Hints NO_HINTS = new Hints();

    @Nullable public final PsiElement target;
    @Nullable public final Integer offsetInElement;

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
