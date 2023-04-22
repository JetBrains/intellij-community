// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiStatement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class IfStatementSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiIfStatement;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>(expandToWholeLine(editorText, e.getTextRange(), false));

    PsiIfStatement statement = (PsiIfStatement)e;

    final PsiKeyword elseKeyword = statement.getElseElement();
    if (elseKeyword != null) {
      final PsiStatement then = statement.getThenBranch();
      if (then != null) {
        final TextRange thenRange = new TextRange(statement.getTextRange().getStartOffset(), then.getTextRange().getEndOffset());
        if (thenRange.contains(cursorOffset)) {
          result.addAll(expandToWholeLine(editorText, thenRange, false));
        }
      }

      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                    statement.getTextRange().getEndOffset()),
                                      false));

      final PsiStatement branch = statement.getElseBranch();
      if (branch instanceof PsiIfStatement elseIf) {
        final PsiKeyword element = elseIf.getElseElement();
        if (element != null) {
          final PsiStatement elseThen = elseIf.getThenBranch();
          if (elseThen != null) {
            result.addAll(expandToWholeLine(editorText,
                                            new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                          elseThen.getTextRange().getEndOffset()),
                                            false));
          }
        }
      }
    }

    return result;
  }
}
