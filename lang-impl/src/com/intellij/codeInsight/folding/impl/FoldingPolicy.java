package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.Language;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

class FoldingPolicy {
  private FoldingPolicy() {}

  public static boolean isCollapseByDefault(PsiElement element) {
    final Language lang = element.getLanguage();
    final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(lang);
    return foldingBuilder != null && foldingBuilder.isCollapsedByDefault(element.getNode());
  }

  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String getSignature(PsiElement element) {
    for(ElementSignatureProvider provider: Extensions.getExtensions(ElementSignatureProvider.EP_NAME)) {
      String signature = provider.getSignature(element);
      if (signature != null) return signature;
    }
    return null;
  }

  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static PsiElement restoreBySignature(PsiFile file, String signature) {
    for(ElementSignatureProvider provider: Extensions.getExtensions(ElementSignatureProvider.EP_NAME)) {
      PsiElement result = provider.restoreBySignature(file, signature);
      if (result != null) return result;
    }
    return null;
  }
}
