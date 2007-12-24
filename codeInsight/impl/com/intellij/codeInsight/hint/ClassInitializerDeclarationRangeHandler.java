package com.intellij.codeInsight.hint;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClassInitializer;

public class ClassInitializerDeclarationRangeHandler implements DeclarationRangeHandler {
  @NotNull
  public TextRange getDeclarationRange(@NotNull final PsiElement container) {
    PsiClassInitializer initializer = (PsiClassInitializer)container;
    int startOffset = initializer.getModifierList().getTextRange().getStartOffset();
    int endOffset = initializer.getBody().getTextRange().getStartOffset();
    return new TextRange(startOffset, endOffset);
  }
}
