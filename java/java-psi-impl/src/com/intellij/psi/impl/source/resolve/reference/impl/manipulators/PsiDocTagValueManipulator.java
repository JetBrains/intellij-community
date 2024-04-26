// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 */
public final class PsiDocTagValueManipulator extends AbstractElementManipulator<PsiDocTag> {

  @Override
  public PsiDocTag handleContentChange(@NotNull PsiDocTag tag, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    final StringBuilder replacement = new StringBuilder( tag.getText() );

    replacement.replace(
      range.getStartOffset(),
      range.getEndOffset(),
      newContent
    );
    return (PsiDocTag)tag.replace(JavaPsiFacade.getElementFactory(tag.getProject()).createDocTagFromText(replacement.toString()));
  }

  @Override
  public @NotNull TextRange getRangeInElement(final @NotNull PsiDocTag tag) {
    final PsiElement[] elements = tag.getDataElements();
    if (elements.length == 0) {
      final PsiElement name = tag.getNameElement();
      final int offset = name.getStartOffsetInParent() + name.getTextLength();
      return new TextRange(offset, offset);
    }
    final PsiElement first = elements[0];
    final PsiElement last = elements[elements.length - 1];
    return new TextRange(first.getStartOffsetInParent(), last.getStartOffsetInParent()+last.getTextLength());
  }
}