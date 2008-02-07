package com.intellij.psi;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author ik
 */
public abstract class PsiReferenceProvider {
 public static final PsiReferenceProvider[] EMPTY_ARRAY = new PsiReferenceProvider[0];

  @NotNull
  public abstract PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context);

}
