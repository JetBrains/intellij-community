package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import com.intellij.patterns.MatchingContext;

/**
 * @author ik
 */
public abstract class PsiReferenceProvider {
 public static final PsiReferenceProvider[] EMPTY_ARRAY = new PsiReferenceProvider[0];

  @NotNull
  public abstract PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final MatchingContext matchingContext);

}
