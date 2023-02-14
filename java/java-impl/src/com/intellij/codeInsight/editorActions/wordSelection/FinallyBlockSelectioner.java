// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiTryStatement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FinallyBlockSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiKeyword && PsiKeyword.FINALLY.equals(e.getText());
  }


  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>();

    final PsiElement parent = e.getParent();
    if (parent instanceof PsiTryStatement tryStatement) {
      final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock != null) {
        result.add(new TextRange(e.getTextRange().getStartOffset(), finallyBlock.getTextRange().getEndOffset()));
      }
    }

    return result;
  }
}
