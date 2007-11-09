package com.intellij.ide.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class PsiElementDataValidator extends DataValidator<PsiElement> {
  @Nullable
  public PsiElement findInvalid(final String dataId, PsiElement psiElement, final Object dataSource) {
    return psiElement.isValid() ? null : psiElement;
  }
}