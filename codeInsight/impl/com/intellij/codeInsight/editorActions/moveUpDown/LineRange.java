package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class LineRange {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.moveUpDown.LineRange");
  final int startLine;
  final int endLine;

  PsiElement firstElement;
  PsiElement lastElement;

  public LineRange(final int startLine, final int endLine) {
    this.startLine = startLine;
    this.endLine = endLine;
    if (startLine > endLine) {
      LOG.error("start > end: start=" + startLine+"; end="+endLine);
    }
  }
  public LineRange(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull Document document) {
    this(document.getLineNumber(startElement.getTextRange().getStartOffset()),
         document.getLineNumber(endElement.getTextRange().getEndOffset()) + 1);
  }

  @NonNls
  public String toString() {
    return "line range: ["+startLine+"-"+endLine+"]";
  }
}
