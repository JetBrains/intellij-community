package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.text.CharArrayUtil;

import java.util.List;

public class DocTagSelectioner extends WordSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiDocTag;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    TextRange range = e.getTextRange();

    int endOffset = range.getEndOffset();
    int startOffset = range.getStartOffset();

    PsiElement[] children = e.getChildren();

    for (int i = children.length - 1; i >= 0; i--) {
      PsiElement child = children[i];

      int childStartOffset = child.getTextRange().getStartOffset();

      if (childStartOffset <= cursorOffset) {
        break;
      }

      if (child instanceof PsiDocToken) {
        PsiDocToken token = (PsiDocToken)child;

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

    startOffset = CharArrayUtil.shiftBackward(editorText, startOffset - 1, "* \t") + 1;

    result.add(new TextRange(startOffset, endOffset));

    return result;
  }
}
