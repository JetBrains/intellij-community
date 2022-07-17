/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CodeBlockOrInitializerSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiCodeBlock || e instanceof PsiArrayInitializerExpression || e instanceof PsiClass && !(e instanceof PsiTypeParameter);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>();
    result.add(getElementRange(e));

    PsiElement[] children = e.getChildren();
    if (children.length > 0) {
      int start = findOpeningBrace(children);

      // in non-Java PsiClasses, there can be no opening brace
      if (start != 0) {
        int end = findClosingBrace(children, start);
        result.addAll(expandToWholeLine(editorText, new TextRange(start, end)));
      }
    }

    return result;
  }

  public TextRange getElementRange(@NotNull PsiElement e) {
    if (e instanceof PsiClass) {
      PsiElement lBrace = ((PsiClass)e).getLBrace();
      PsiElement rBrace = ((PsiClass)e).getRBrace();
      if (lBrace != null && rBrace != null) {
        return new TextRange(lBrace.getTextOffset(), rBrace.getTextRange().getEndOffset());
      }
    }

    return e.getTextRange();
  }

  public static int findOpeningBrace(PsiElement[] children) {
    int start = 0;
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACE)) {
        int j = i + 1;

        while (children[j] instanceof PsiWhiteSpace) {
          j++;
        }

        start = children[j].getTextRange().getStartOffset();
      }
    }
    return start;
  }

  public static int findClosingBrace(PsiElement[] children, int startOffset) {
    int end = children[children.length - 1].getTextRange().getEndOffset();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (PsiUtil.isJavaToken(child, JavaTokenType.RBRACE)) {
        int j = i - 1;

        while (children[j] instanceof PsiWhiteSpace && children[j].getTextRange().getStartOffset() > startOffset) {
          j--;
        }

        end = children[j].getTextRange().getEndOffset();
      }
    }
    return end;
  }
}
