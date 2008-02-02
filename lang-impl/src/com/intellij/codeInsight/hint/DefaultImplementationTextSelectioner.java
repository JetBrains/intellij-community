/*
 * User: anna
 * Date: 01-Feb-2008
 */
package com.intellij.codeInsight.hint;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class DefaultImplementationTextSelectioner implements ImplementationTextSelectioner {
  public int getTextStartOffset(@NotNull final PsiElement parent) {
    return parent.getTextRange().getStartOffset();
  }

  public int getTextEndOffset(@NotNull PsiElement element) {
    return element.getTextRange().getEndOffset();
  }
}