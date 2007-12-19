package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface ElementSignatureProvider {
  ExtensionPointName<ElementSignatureProvider> EP_NAME = ExtensionPointName.create("com.intellij.elementSignatureProvider");

  @Nullable
  String getSignature(PsiElement element);
  PsiElement restoreBySignature(PsiFile file, String signature);
}
