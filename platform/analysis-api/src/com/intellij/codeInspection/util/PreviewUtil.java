// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PreviewUtil {
  /**
   * Returns the same element in the file copy.
   * 
   * @param element an element to find
   * @param copy file that must be a copy of {@code element.getContainingFile()}
   * @return found element; null if input element is null
   * @throws IllegalStateException if it's detected that the supplied file is not exact copy of original file. 
   * The exception is thrown on a best-effort basis, so you cannot rely on it. 
   */
  @Contract("null, _ -> null; !null, _ -> !null")
  public static <T extends PsiElement> T findSameElementInCopy(@Nullable T element, @NotNull PsiFile copy) throws IllegalStateException {
    if (element == null) return null;
    if (element.getClass().equals(copy.getClass())) {
      //noinspection unchecked
      return (T)copy;
    }
    TextRange range = element.getTextRange();
    if (range.getLength() == 0 && range.getStartOffset() > 0) {
      PsiElement predecessor = copy.findElementAt(range.getStartOffset()-1);
      while (predecessor != null) {
        PsiElement newElement = predecessor.getNextSibling();
        if (newElement != null) {
          TextRange newRange = newElement.getTextRange();
          if (newRange.equals(range) && newElement.getClass().equals(element.getClass())) {
            //noinspection unchecked
            return (T)newElement;
          }
        }
        if (predecessor.getTextRange().getEndOffset() > range.getEndOffset()) break;
        predecessor = predecessor.getParent();
      }
    } else {
      PsiElement newElement = copy.findElementAt(range.getStartOffset());
      while (newElement != null) {
        TextRange newRange = newElement.getTextRange();
        if (newRange.equals(range) && newElement.getClass().equals(element.getClass())) {
          //noinspection unchecked
          return (T)newElement;
        }
        if (newRange.getStartOffset() < range.getStartOffset() || newRange.getEndOffset() > range.getEndOffset()) break;
        newElement = newElement.getParent();
      }
    }
    throw new IllegalStateException("Cannot find element in copy file");
  }
}
