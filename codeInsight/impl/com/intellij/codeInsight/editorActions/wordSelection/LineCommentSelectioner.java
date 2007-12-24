package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;

public class LineCommentSelectioner extends WordSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiComment && !(e instanceof PsiDocComment);
  }

  public List<TextRange> select(PsiElement element, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(element, editorText, cursorOffset, editor);


    PsiElement firstComment = element;
    PsiElement e = element;

    while (e.getPrevSibling() != null) {
      if (e instanceof PsiComment) {
        firstComment = e;
      }
      else if (!(e instanceof PsiWhiteSpace)) {
        break;
      }
      e = e.getPrevSibling();
    }

    PsiElement lastComment = element;
    e = element;
    while (e.getNextSibling() != null) {
      if (e instanceof PsiComment) {
        lastComment = e;
      }
      else if (!(e instanceof PsiWhiteSpace)) {
        break;
      }
      e = e.getNextSibling();
    }


    result.addAll(expandToWholeLine(editorText, new TextRange(firstComment.getTextRange().getStartOffset(),
                                                              lastComment.getTextRange().getEndOffset())));

    return result;
  }
}
