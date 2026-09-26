// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiJavaToken;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class ForStatementHeaderSelectioner implements ExtendWordSelectionHandler {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiForStatement || e instanceof PsiForeachStatement;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    PsiJavaToken lParen = e instanceof PsiForStatement ? ((PsiForStatement)e).getLParenth()
                                                       : e instanceof PsiForeachStatement ? ((PsiForeachStatement)e).getLParenth() : null;
    PsiJavaToken rParen = e instanceof PsiForStatement ? ((PsiForStatement)e).getRParenth()
                                                       : e instanceof PsiForeachStatement ? ((PsiForeachStatement)e).getRParenth() : null;
    if (lParen == null || rParen == null) return null;
    TextRange result = new TextRange(lParen.getTextRange().getEndOffset(), rParen.getTextRange().getStartOffset());
    return result.containsOffset(cursorOffset) ? Collections.singletonList(result) : null;
  }
}
