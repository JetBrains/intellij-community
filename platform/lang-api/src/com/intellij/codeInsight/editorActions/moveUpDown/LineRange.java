// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LineRange {
  private static final Logger LOG = Logger.getInstance(LineRange.class);
  public final int startLine;
  public final int endLine;

  public PsiElement firstElement;
  public PsiElement lastElement;

  public LineRange(final int startLine, final int endLine) {
    this.startLine = startLine;
    this.endLine = endLine;
    LOG.assertTrue(startLine >= 0, "Negative start line");
    if (startLine > endLine) {
      LOG.error("start > end: start=" + startLine+"; end="+endLine);
    }
  }
  public LineRange(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull Document document) {
    this(document.getLineNumber(startElement.getTextRange().getStartOffset()),
         document.getLineNumber(endElement.getTextRange().getEndOffset()) + 1);
  }

  public LineRange(@NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    this(startElement, endElement, startElement.getContainingFile().getViewProvider().getDocument());
  }

  public LineRange(@NotNull PsiElement element) {
    this(element, element);
  }

  @Override
  public @NonNls String toString() {
    return "line range: ["+startLine+"-"+endLine+"]";
  }

  public boolean containsLine(int lineNumber) {
    return startLine <= lineNumber && endLine > lineNumber;
  }

  public boolean contains(LineRange range) {
    return startLine <= range.startLine && endLine >= range.endLine;
  }


}
