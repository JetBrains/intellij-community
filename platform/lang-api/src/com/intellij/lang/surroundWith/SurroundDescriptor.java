// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.surroundWith;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Defines a code fragment type on which the <em>Code | Surround With</em> action can be used for files
 * in a custom language. All surround descriptors registered for a language are queried
 * sequentially, and as soon as one is found that returns a non-empty list of elements
 * from {@link #getElementsToSurround(PsiFile, int, int)}, the user
 * is prompted to choose a specific surrounder for that surround descriptor.
 *
 * @see com.intellij.lang.LanguageSurrounders
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/surround-with.html">Surround With (IntelliJ Platform Docs)</a>
 */
public interface SurroundDescriptor {
  /**
   * Returns the array of elements which will be included in the surrounded region for
   * the specified selection in the specified file, or an empty array if no surrounders
   * from this surround descriptor are applicable to the specified selection.
   *
   * @param file        the file where elements are to be surrounded
   * @param startOffset the selection start offset, with whitespaces skipped
   * @param endOffset   the selection end offset, with whitespaces skipped
   * @return the elements to be surrounded, or an empty array if cannot surround
   */
  PsiElement @NotNull [] getElementsToSurround(PsiFile file, int startOffset, int endOffset);

  /**
   * @return the array of surrounders (surround templates) which can be used for this code fragment type
   */
  Surrounder @NotNull [] getSurrounders();

  /**
   * If {@code true} then only surrounders from all applicable exclusive surround descriptors
   * will be included in the <em>Code | Surround With</em> action.
   */
  boolean isExclusive();
}
