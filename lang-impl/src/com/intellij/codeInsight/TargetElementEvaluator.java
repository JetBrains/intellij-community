package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author maxim
 */
public interface TargetElementEvaluator {
  ExtensionPointName<TargetElementEvaluator> EP_NAME = ExtensionPointName.create("com.intellij.targetElementEvaluator");

  boolean includeSelfInGotoImplementation(@NotNull PsiElement element);

  @Nullable
  PsiElement getElementByReference(PsiReference ref, final int flags);
}