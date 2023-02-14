// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ListSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiParameterList || e instanceof PsiExpressionList || e instanceof PsiRecordHeader;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {

    PsiElement[] children = e.getChildren();

    int start = 0;
    int end = 0;

    for (PsiElement child : children) {
      if (child instanceof PsiJavaToken token) {
        if (token.getTokenType() == JavaTokenType.LPARENTH) {
          start = token.getTextOffset() + 1;
        }
        if (token.getTokenType() == JavaTokenType.RPARENTH) {
          end = token.getTextOffset();
        }
      }
    }

    List<TextRange> result = new ArrayList<>();
    if (start != 0 && end != 0) {
      result.add(new TextRange(start, end));
    }
    return result;
  }
}
