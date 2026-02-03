// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Provided a way to customize "extended" completion behaviour.
 * <p>
 * Use case:
 * By default almost all imports in javascript are inserted without extension,
 * while the default behaviour of the file path inserter is to insert full file name as the reference text.
 */
public interface FileReferenceWithExtendedCompletion {
  void bindToExtendedElement(final @NotNull PsiElement element) throws IncorrectOperationException;
}
