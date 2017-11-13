/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class LiteralSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    PsiElement parent = e.getParent();
    return
      isStringLiteral(e) || isStringLiteral(parent);
  }

  private static boolean isStringLiteral(PsiElement element) {
    final PsiType type = element instanceof PsiLiteralExpression ? ((PsiLiteralExpression)element).getType() : null;
    return  type != null && type.equalsToText(JAVA_LANG_STRING)
            && element.getText().startsWith("\"")
            && element.getText().endsWith("\"");
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    TextRange range = e.getTextRange();
    SelectWordUtil.addWordHonoringEscapeSequences(editorText, range, cursorOffset,
                                                  new StringLiteralLexer('\"', JavaTokenType.STRING_LITERAL),
                                                  result);

    result.add(new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1));

    return result;
  }
}
