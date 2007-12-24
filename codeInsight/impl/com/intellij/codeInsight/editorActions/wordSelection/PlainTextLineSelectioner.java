package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainText;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;
import java.util.ArrayList;

public class PlainTextLineSelectioner extends BasicSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiPlainText;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    return selectPlainTextLine(e, editorText, cursorOffset);
  }

  public static List<TextRange> selectPlainTextLine(final PsiElement e, final CharSequence editorText, final int cursorOffset) {
    int start = cursorOffset;
    while (start > 0 && editorText.charAt(start - 1) != '\n' && editorText.charAt(start - 1) != '\r') start--;

    int end = cursorOffset;
    while (end < editorText.length() && editorText.charAt(end) != '\n' && editorText.charAt(end) != '\r') end++;

    final TextRange range = new TextRange(start, end);
    if (!e.getParent().getTextRange().contains(range)) return null;
    List<TextRange> result = new ArrayList<TextRange>();
    result.add(range);
    return result;
  }
}
