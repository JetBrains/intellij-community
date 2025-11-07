// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface BaseCompletionParameters {
  /**
   * Return the leaf PSI element in the "completion file" at offset {@link #getOffset()}.
   * <p>
   * "Completion file" is a PSI file used for completion purposes. Most often it's a non-physical copy of the file being edited
   * (the original file can be accessed from {@link PsiFile#getOriginalFile()} or {@link #getOriginalFile()}).
   * </p>
   * <p>
   * A special 'dummy identifier' string is inserted to the copied file at caret offset (removing the selection).
   * Most often this string is an identifier (see {@link CompletionInitializationContext#DUMMY_IDENTIFIER}).
   * It can be changed via {@link CompletionContributor#beforeCompletion(CompletionInitializationContext)} method.
   * </p>
   * <p>
   * Why? This way there'll always be some non-empty element there, which usually reduces the number of
   * possible cases to be considered inside a {@link CompletionContributor}.
   * Also, even if completion was invoked in the middle of a white space, a reference might appear there after dummy identifier is inserted,
   * and its {@link com.intellij.psi.PsiReference#getVariants()} can then be suggested.
   * </p>
   * <p>
   * If the dummy identifier is empty, then the file isn't copied and this method returns whatever is at caret in the original file.
   */
  @NotNull PsiElement getPosition();

  /**
   * @return the offset (relative to the file) where code completion was invoked.
   */
  int getOffset();

  /**
   * @return the file being edited, possibly injected, where code completion was invoked.
   */
  @NotNull PsiFile getOriginalFile();
}