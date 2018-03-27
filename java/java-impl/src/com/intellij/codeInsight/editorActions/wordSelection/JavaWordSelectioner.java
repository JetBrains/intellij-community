/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

/**
 * @author peter
 */
public class JavaWordSelectioner extends AbstractWordSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    if (e instanceof PsiKeyword) {
      return true;
    }
    if (e instanceof PsiJavaToken) {
      IElementType tokenType = ((PsiJavaToken)e).getTokenType();
      return tokenType == JavaTokenType.IDENTIFIER || tokenType == JavaTokenType.STRING_LITERAL;
    }
    return false;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> ranges = super.select(e, editorText, cursorOffset, editor);
    if (PsiUtil.isJavaToken(e, JavaTokenType.STRING_LITERAL)) {
      killRangesBreakingEscapes(e, ranges, e.getTextRange());
    }
    return ranges;
  }

  private static void killRangesBreakingEscapes(PsiElement e, List<TextRange> ranges, TextRange literalRange) {
    for (Iterator<TextRange> iterator = ranges.iterator(); iterator.hasNext(); ) {
      TextRange each = iterator.next();
      if (literalRange.contains(each) &&
          literalRange.getStartOffset() < each.getStartOffset() &&
          e.getText().charAt(each.getStartOffset() - literalRange.getStartOffset() - 1) == '\\') {
        iterator.remove();
      }
    }
  }
}
