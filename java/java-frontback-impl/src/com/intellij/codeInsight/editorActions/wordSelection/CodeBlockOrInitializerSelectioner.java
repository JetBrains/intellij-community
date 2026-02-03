// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class CodeBlockOrInitializerSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiCodeBlock ||
           e instanceof PsiArrayInitializerExpression ||
           e instanceof PsiClass && !(e instanceof PsiTypeParameter);
  }


  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>();
    result.add(getElementRange(e));

    PsiElement[] children = e.getChildren();
    if (children.length > 0) {
      int start = findOpeningBrace(children);

      // in non-Java PsiClasses, there can be no opening brace
      if (start != 0) {
        int end = findClosingBrace(children, start);
        result.addAll(expandToWholeLine(editorText, new TextRange(start, end)));
      }
    }

    return result;
  }

  public TextRange getElementRange(@NotNull PsiElement e) {
    if (e instanceof PsiClass) {
      PsiElement lBrace = ((PsiClass)e).getLBrace();
      PsiElement rBrace = ((PsiClass)e).getRBrace();
      if (lBrace != null && rBrace != null) {
        return new TextRange(lBrace.getTextOffset(), rBrace.getTextRange().getEndOffset());
      }
    }

    return e.getTextRange();
  }

  public static int findOpeningBrace(PsiElement[] children) {
    int start = 0;
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACE)) {
        int j = i + 1;

        while (children[j] instanceof PsiWhiteSpace) {
          j++;
        }

        start = children[j].getTextRange().getStartOffset();
      }
    }
    return start;
  }

  public static int findClosingBrace(PsiElement[] children, int startOffset) {
    int end = children[children.length - 1].getTextRange().getEndOffset();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (PsiUtil.isJavaToken(child, JavaTokenType.RBRACE)) {
        int j = i - 1;

        while (children[j] instanceof PsiWhiteSpace && children[j].getTextRange().getStartOffset() > startOffset) {
          j--;
        }

        end = children[j].getTextRange().getEndOffset();
      }
    }
    return end;
  }
}
