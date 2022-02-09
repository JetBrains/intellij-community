// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.codeStyle.JavaFileCodeStyleFacade;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SnippetDocTagManipulator extends AbstractElementManipulator<PsiSnippetDocTagImpl> {

  @Override
  public PsiSnippetDocTagImpl handleContentChange(@NotNull PsiSnippetDocTagImpl element,
                                                  @NotNull TextRange range,
                                                  String newContent) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());

    final JavaFileCodeStyleFacade codeStyleFacade = JavaFileCodeStyleFacade.forContext(element.getContainingFile());
    final String newSnippetTagContent = codeStyleFacade.isJavaDocLeadingAsterisksEnabled()
                                        ? prependAbsentAsterisks(newContent)
                                        : newContent;

    final PsiDocComment text = factory.createDocCommentFromText("/**\n" + newSnippetTagContent + "\n*/");
    final PsiSnippetDocTag snippet = PsiTreeUtil.findChildOfType(text, PsiSnippetDocTag.class);
    if (snippet == null) {
      return element;
    }
    return (PsiSnippetDocTagImpl)element.replace(snippet);
  }

  @Contract(pure = true)
  private static @NotNull String prependAbsentAsterisks(@NotNull String input) {
    final StringBuilder builder = new StringBuilder();
    boolean afterNewLine = false;
    for (char c : input.toCharArray()) {
      if (c == '\n') {
        afterNewLine = true;
      }
      else if (afterNewLine) {
        if (c == '*') {
          afterNewLine = false;
        }
        else if (!Character.isWhitespace(c)) {
          builder.append("* ");
          afterNewLine = false;
        }
      }
      builder.append(c);
    }
    return builder.toString();
  }

  @Override
  public @NotNull TextRange getRangeInElement(@NotNull PsiSnippetDocTagImpl element) {
    final List<TextRange> ranges = element.getContentRanges();
    if (ranges.isEmpty()) return TextRange.EMPTY_RANGE;
    final int startOffset = ranges.get(0).getStartOffset();
    final int endOffset = ContainerUtil.getLastItem(ranges).getEndOffset();
    return TextRange.create(startOffset, endOffset);
  }
}
