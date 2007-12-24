package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;
import java.util.ArrayList;

public class FinallyBlockSelectioner extends BasicSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiKeyword && PsiKeyword.FINALLY.equals(e.getText());
  }


  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = new ArrayList<TextRange>();

    final PsiElement parent = e.getParent();
    if (parent instanceof PsiTryStatement) {
      final PsiTryStatement tryStatement = (PsiTryStatement)parent;
      final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock != null) {
        result.add(new TextRange(e.getTextRange().getStartOffset(), finallyBlock.getTextRange().getEndOffset()));
      }
    }

    return result;
  }
}
