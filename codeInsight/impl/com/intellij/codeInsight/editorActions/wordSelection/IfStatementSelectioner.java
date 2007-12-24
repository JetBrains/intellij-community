package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiStatement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;
import java.util.ArrayList;

public class IfStatementSelectioner extends BasicSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiIfStatement;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = new ArrayList<TextRange>();
    result.addAll(expandToWholeLine(editorText, e.getTextRange(), false));

    PsiIfStatement statement = (PsiIfStatement)e;

    final PsiKeyword elseKeyword = statement.getElseElement();
    if (elseKeyword != null) {
      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                    statement.getTextRange().getEndOffset()),
                                      false));

      final PsiStatement branch = statement.getElseBranch();
      if (branch instanceof PsiIfStatement) {
        PsiIfStatement elseIf = (PsiIfStatement)branch;
        final PsiKeyword element = elseIf.getElseElement();
        if (element != null) {
          result.addAll(expandToWholeLine(editorText,
                                          new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                        elseIf.getThenBranch().getTextRange().getEndOffset()),
                                          false));
        }
      }
    }

    return result;
  }
}
