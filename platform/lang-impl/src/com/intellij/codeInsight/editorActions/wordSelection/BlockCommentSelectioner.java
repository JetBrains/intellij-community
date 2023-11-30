// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class BlockCommentSelectioner implements ExtendWordSelectionHandler {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiComment && !(e instanceof PsiDocCommentBase);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(e.getLanguage());
    if (commenter == null) return null;
    String blockStart = commenter.getBlockCommentPrefix();
    String blockEnd = commenter.getBlockCommentSuffix();
    if (blockStart == null || blockEnd == null) return null;
    String elementText = e.getText();
    if (elementText == null || !elementText.startsWith(blockStart) || !elementText.endsWith(blockEnd)) return null;
    TextRange elementRange = e.getTextRange();
    return Collections.singletonList(new TextRange(elementRange.getStartOffset() + blockStart.length(),
                                                   elementRange.getEndOffset() - blockEnd.length()));
  }
}
