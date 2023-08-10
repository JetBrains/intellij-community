// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiTypeCastExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TypeCastSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiTypeCastExpression;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>(expandToWholeLine(editorText, e.getTextRange(), false));

    PsiTypeCastExpression expression = (PsiTypeCastExpression)e;
    PsiElement[] children = expression.getChildren();
    PsiElement lParen = null;
    PsiElement rParen = null;
    for (PsiElement child : children) {
      if (child instanceof PsiJavaToken token) {
        if (token.getTokenType() == JavaTokenType.LPARENTH) lParen = token;
        if (token.getTokenType() == JavaTokenType.RPARENTH) rParen = token;
      }
    }

    if (lParen != null && rParen != null) {
      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(lParen.getTextRange().getStartOffset(),
                                                    rParen.getTextRange().getEndOffset()),
                                      false));
    }

    return result;
  }
}
