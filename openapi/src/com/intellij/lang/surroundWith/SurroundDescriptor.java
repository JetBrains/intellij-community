package com.intellij.lang.surroundWith;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public interface SurroundDescriptor {
  /**
   *
   * @param file where elements are to be surround
   * @param startOffset with whitespaces skipped
   * @param endOffset with whitespaces skipped
   * @return elements or empty array if cannot surround
   */
  @NotNull
  PsiElement[] getElementsToSurround (PsiFile file, int startOffset, int endOffset);

  @NotNull
  Surrounder[] getSurrounders();
}
