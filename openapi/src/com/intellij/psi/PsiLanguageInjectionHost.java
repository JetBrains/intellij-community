package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public interface PsiLanguageInjectionHost extends PsiElement {

  @Nullable
  PsiElement getInjectedPsi();
}
