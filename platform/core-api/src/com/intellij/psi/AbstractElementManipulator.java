// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class AbstractElementManipulator<T extends PsiElement> implements ElementManipulator<T> {

  @Override
  public T handleContentChange(final @NotNull T element, final String newContent) throws IncorrectOperationException {
    return handleContentChange(element, getRangeInElement(element), newContent);
  }

  @Override
  public @NotNull TextRange getRangeInElement(final @NotNull T element) {
    return new TextRange(0, element.getTextLength());
  }
}
