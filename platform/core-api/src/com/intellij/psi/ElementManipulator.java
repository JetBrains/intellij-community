// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles value text change and provides its underlying TextRange for specific PsiElement.
 *
 * @see AbstractElementManipulator
 * @see ElementManipulators
 */
public interface ElementManipulator<T extends PsiElement> {

  /**
   * Changes the element's text to the given new text.
   *
   * @param element    element to be changed
   * @param range      range within the element
   * @param newContent new element text
   * @return changed element
   * @throws IncorrectOperationException if something goes wrong
   */
  @Nullable
  T handleContentChange(@NotNull T element, @NotNull TextRange range, String newContent) throws IncorrectOperationException;

  @Nullable
  T handleContentChange(@NotNull T element, String newContent) throws IncorrectOperationException;

  /**
   * Returns value text range.
   */
  @NotNull
  TextRange getRangeInElement(@NotNull T element);
}
