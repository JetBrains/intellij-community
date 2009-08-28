package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PsiNameIdentifierOwner extends PsiNamedElement {
  @Nullable
  PsiElement getNameIdentifier();
}
