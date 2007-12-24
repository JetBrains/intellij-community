package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import com.intellij.codeInsight.editorActions.SelectWordUtil;

import java.util.List;

/**
   *
   */

public class MethodOrClassSelectioner extends BasicSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiClass && !(e instanceof PsiTypeParameter) || e instanceof PsiMethod;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    PsiElement firstChild = e.getFirstChild();
    PsiElement[] children = e.getChildren();

    if (firstChild instanceof PsiDocComment) {
      int i = 1;

      while (children[i] instanceof PsiWhiteSpace) {
        i++;
      }

      TextRange range = new TextRange(children[i].getTextRange().getStartOffset(), e.getTextRange().getEndOffset());
      result.addAll(expandToWholeLine(editorText, range));

      range = new TextRange(firstChild.getTextRange().getStartOffset(), firstChild.getTextRange().getEndOffset());
      result.addAll(expandToWholeLine(editorText, range));
    }
    else if (firstChild instanceof PsiComment) {
      int i = 1;

      while (children[i] instanceof PsiComment || children[i] instanceof PsiWhiteSpace) {
        i++;
      }
      PsiElement last = children[i - 1] instanceof PsiWhiteSpace ? children[i - 2] : children[i - 1];
      TextRange range = new TextRange(firstChild.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());
      if (range.contains(cursorOffset)) {
        result.addAll(expandToWholeLine(editorText, range));
      }

      range = new TextRange(children[i].getTextRange().getStartOffset(), e.getTextRange().getEndOffset());
      result.addAll(expandToWholeLine(editorText, range));
    }

    if (e instanceof PsiClass) {
      int start = CodeBlockOrInitializerSelectioner.findOpeningBrace(children);
      int end = CodeBlockOrInitializerSelectioner.findClosingBrace(children, start);

      result.addAll(expandToWholeLine(editorText, new TextRange(start, end)));
    }


    return result;
  }
}
