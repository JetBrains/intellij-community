// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class PsiPolyVariantReferenceBase<T extends PsiElement> extends PsiReferenceBase<T> implements PsiPolyVariantReference {

  public PsiPolyVariantReferenceBase(final @NotNull T psiElement) {
    super(psiElement);
  }

  public PsiPolyVariantReferenceBase(@NotNull T element, TextRange range) {
    super(element, range);
  }

  public PsiPolyVariantReferenceBase(final @NotNull T psiElement, final boolean soft) {
    super(psiElement, soft);
  }

  public PsiPolyVariantReferenceBase(final @NotNull T element, final TextRange range, final boolean soft) {
    super(element, range, soft);
  }

  @Override
  public @Nullable PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    final ResolveResult[] results = multiResolve(false);
    for (ResolveResult result : results) {
      if (getElement().getManager().areElementsEquivalent(result.getElement(), element)) {
        return true;
      }
    }
    return false;
  }
}
