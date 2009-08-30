package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.util.text.CharArrayUtil;

import java.util.List;

public class DocCommentSelectioner extends LineCommentSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiDocComment;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    PsiElement[] children = e.getChildren();

    int startOffset = e.getTextRange().getStartOffset();
    int endOffset = e.getTextRange().getEndOffset();

    for (PsiElement child : children) {
      if (child instanceof PsiDocToken) {
        PsiDocToken token = (PsiDocToken)child;

        if (token.getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
          char[] chars = token.getText().toCharArray();

          if (CharArrayUtil.shiftForward(chars, 0, " *\n\t\r") != chars.length) {
            break;
          }
        }
      }

      startOffset = child.getTextRange().getEndOffset();
    }

    for (PsiElement child : children) {
      if (child instanceof PsiDocToken) {
        PsiDocToken token = (PsiDocToken)child;

        if (token.getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
          char[] chars = token.getText().toCharArray();

          if (CharArrayUtil.shiftForward(chars, 0, " *\n\t\r") != chars.length) {
            endOffset = child.getTextRange().getEndOffset();
          }
        }
      }
    }

    startOffset = CharArrayUtil.shiftBackward(editorText, startOffset - 1, "* \t") + 1;

    result.add(new TextRange(startOffset, endOffset));

    return result;
  }
}
