/*
 * User: anna
 * Date: 01-Feb-2008
 */
package com.intellij.codeInsight.hint;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface ImplementationTextSelectioner {
  int getTextStartOffset(@NotNull PsiElement element);

  int getTextEndOffset(@NotNull PsiElement element);
}