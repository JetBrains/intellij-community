package com.intellij.codeInspection;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * This kind of suppression fix is able to provide suppression container. 
 * Container might be used by IDEA for checking fix availability and highlighting suppression element.
 */
public interface ContainerBasedSuppressQuickFix extends SuppressQuickFix {
  @Nullable
  PsiElement getContainer(@Nullable PsiElement context);
}
