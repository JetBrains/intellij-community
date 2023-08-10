// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DocTagSelectioner extends WordSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiDocTag;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
    result.add(getDocTagRange((PsiDocTag)e, editorText, cursorOffset));
    return result;
  }

  public static TextRange getDocTagRange(PsiDocTag e, CharSequence documentText, int minOffset) {
    TextRange range = e.getTextRange();

    int endOffset = range.getEndOffset();
    int startOffset = range.getStartOffset();

    PsiElement[] children = e.getChildren();

    for (int i = children.length - 1; i >= 0; i--) {
      PsiElement child = children[i];

      int childStartOffset = child.getTextRange().getStartOffset();

      if (childStartOffset <= minOffset) {
        break;
      }

      if (child instanceof PsiDocToken token) {
        IElementType type = token.getTokenType();
        char[] chars = token.textToCharArray();
        int shift = CharArrayUtil.shiftForward(chars, 0, " \t\n\r");

        if (shift != chars.length && type != JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
          break;
        }
      }
      else if (!(child instanceof PsiWhiteSpace)) {
        break;
      }

      endOffset = Math.min(childStartOffset, endOffset);
    }

    startOffset = CharArrayUtil.shiftBackward(documentText, startOffset - 1, "* \t") + 1;

    return new TextRange(startOffset, endOffset);
  }
}
