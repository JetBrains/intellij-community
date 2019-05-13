/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AntLikePropertySelectionHandler extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    Language l = e.getLanguage();
    if (!(l.equals(JavaLanguage.INSTANCE)
          || l.equals(StdLanguages.XML)
          || l.equals(StdLanguages.ANT))) {
      return false;
    }

    return PsiTreeUtil.getParentOfType(e, PsiComment.class) == null;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
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
