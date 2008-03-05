package com.intellij.codeInsight.hint;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

public class MethodDeclarationRangeHandler implements DeclarationRangeHandler {
  @NotNull
  public TextRange getDeclarationRange(@NotNull final PsiElement container) {
    PsiMethod method = (PsiMethod)container;
    final TextRange textRange = method.getModifierList().getTextRange();
    int startOffset = textRange != null ? textRange.getStartOffset():method.getTextOffset();
    int endOffset = method.getThrowsList().getTextRange().getEndOffset();
    return new TextRange(startOffset, endOffset);
  }
}
