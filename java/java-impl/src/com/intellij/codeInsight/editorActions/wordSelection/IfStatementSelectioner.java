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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiStatement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class IfStatementSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiIfStatement;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>(expandToWholeLine(editorText, e.getTextRange(), false));

    PsiIfStatement statement = (PsiIfStatement)e;

    final PsiKeyword elseKeyword = statement.getElseElement();
    if (elseKeyword != null) {
      final PsiStatement then = statement.getThenBranch();
      if (then != null) {
        final TextRange thenRange = new TextRange(statement.getTextRange().getStartOffset(), then.getTextRange().getEndOffset());
        if (thenRange.contains(cursorOffset)) {
          result.addAll(expandToWholeLine(editorText, thenRange, false));
        }
      }

      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                    statement.getTextRange().getEndOffset()),
                                      false));

      final PsiStatement branch = statement.getElseBranch();
      if (branch instanceof PsiIfStatement) {
        PsiIfStatement elseIf = (PsiIfStatement)branch;
        final PsiKeyword element = elseIf.getElseElement();
        if (element != null) {
          final PsiStatement elseThen = elseIf.getThenBranch();
          if (elseThen != null) {
            result.addAll(expandToWholeLine(editorText,
                                            new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                          elseThen.getTextRange().getEndOffset()),
                                            false));
          }
        }
      }
    }

    return result;
  }
}
