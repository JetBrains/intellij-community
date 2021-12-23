// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiSnippetDocTagBody;
import com.intellij.psi.javadoc.PsiSnippetDocTagValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

public final class SnippetDocTagManipulator extends AbstractElementManipulator<PsiSnippetDocTagImpl> {

  @Override
  public PsiSnippetDocTagImpl handleContentChange(@NotNull PsiSnippetDocTagImpl element,
                                                  @NotNull TextRange range,
                                                  String newContent) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @NotNull TextRange getRangeInElement(@NotNull PsiSnippetDocTagImpl element) {
    final PsiSnippetDocTagValue valueElement = element.getValueElement();
    if (valueElement == null) return super.getRangeInElement(element);

    final PsiSnippetDocTagBody body = valueElement.getBody();
    if (body == null) return super.getRangeInElement(element);

    Optional<PsiElement> first = Arrays.stream(body.getChildren())
      .filter(e -> e.getNode().getElementType() == JavaDocTokenType.DOC_COMMENT_DATA)
      .findFirst();
    if (!first.isPresent()) {
      return super.getRangeInElement(element);
    }
    final PsiElement start = first.get();
    PsiElement last = start;
    for (PsiElement e = start.getNextSibling(); e != null; e = e.getNextSibling()) {
      if (e.getNode().getElementType() == JavaDocTokenType.DOC_COMMENT_DATA) {
        last = e;
      }
    }
    final TextRange elementTextRange = element.getTextRange();
    return TextRange.from(start.getTextRange().getStartOffset() - elementTextRange.getStartOffset(),
                         last.getTextRange().getEndOffset() - start.getTextRange().getStartOffset());
  }
}
