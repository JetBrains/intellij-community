package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AntLikePropertySelectionHandler implements ExtendWordSelectionHandler {
  public boolean canSelect(PsiElement e) {
    Language l = e.getLanguage();
    if (!(l.equals(StdLanguages.JAVA)
          || l.equals(StdLanguages.XML)
          || l.equals(StdLanguages.ANT))) {
      return false;
    }

    return PsiTreeUtil.getParentOfType(e, PsiComment.class) == null;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    TextRange range = e.getTextRange();
    char prevLeftChar = ' ';
    for (int left = cursorOffset; left >= range.getStartOffset(); left--) {
      char leftChar = editorText.charAt(left);
      if (leftChar == '}') return Collections.emptyList();
      if (leftChar == '$' && prevLeftChar == '{') {
        for (int right = cursorOffset; right < range.getEndOffset(); right++) {
          char rightChar = editorText.charAt(right);
          if (rightChar == '{') return Collections.emptyList();
          if (rightChar == '}') {
            return Arrays.asList(new TextRange(left + 2, right), new TextRange(left, right + 1));
          }
        }
      }
      prevLeftChar = leftChar;
    }
    return Collections.emptyList();
  }
}
