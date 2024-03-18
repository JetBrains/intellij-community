// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_JAVA_COMMENT_BIT_SET;

public final class AntLikePropertySelectionHandler extends ExtendWordSelectionHandlerBase {
  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    TextRange range = e.getTextRange();
    char prevLeftChar = ' ';
    for (int left = Math.min(cursorOffset, editor.getDocument().getTextLength() - 1); left >= range.getStartOffset(); left--) {
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

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    Language l = e.getLanguage();
    if (!(l.equals(JavaLanguage.INSTANCE)
          || l.equals(XMLLanguage.INSTANCE))) {
      return false;
    }

    if (BasicJavaAstTreeUtil.getParentOfType(BasicJavaAstTreeUtil.toNode(e), BASIC_JAVA_COMMENT_BIT_SET) == null) {
      return true;
    }
    return PsiTreeUtil.getParentOfType(e, PsiComment.class) == null;
  }
}
