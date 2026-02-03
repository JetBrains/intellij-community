// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainText;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class PlainTextLineSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiPlainText;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    return selectPlainTextLine(e, editorText, cursorOffset);
  }

  public static List<TextRange> selectPlainTextLine(final PsiElement e, final CharSequence editorText, final int cursorOffset) {
    int start = cursorOffset;
    while (start > 0 && editorText.charAt(start - 1) != '\n' && editorText.charAt(start - 1) != '\r') start--;

    int end = cursorOffset;
    while (end < editorText.length() && editorText.charAt(end) != '\n' && editorText.charAt(end) != '\r') end++;

    final TextRange range = new TextRange(start, end);
    if (!e.getParent().getTextRange().contains(range)) return null;
    List<TextRange> result = new ArrayList<>();
    result.add(range);
    return result;
  }
}
