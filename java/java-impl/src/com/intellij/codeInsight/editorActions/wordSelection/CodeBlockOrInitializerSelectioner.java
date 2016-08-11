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

import java.util.ArrayList;
import java.util.List;

public class CodeBlockOrInitializerSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiCodeBlock || e instanceof PsiArrayInitializerExpression;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = new ArrayList<>();
    result.add(e.getTextRange());

    PsiElement[] children = e.getChildren();
    if (children.length > 0) {
      int start = findOpeningBrace(children);
      int end = findClosingBrace(children, start);
      result.addAll(expandToWholeLine(editorText, new TextRange(start, end)));
    }

    return result;
  }

  public static int findOpeningBrace(PsiElement[] children) {
    int start = 0;
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (child instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)child;

        if (token.getTokenType() == JavaTokenType.LBRACE) {
          int j = i + 1;

          while (children[j] instanceof PsiWhiteSpace) {
            j++;
          }

          start = children[j].getTextRange().getStartOffset();
        }
      }
    }
    return start;
  }

  public static int findClosingBrace(PsiElement[] children, int startOffset) {
    int end = children[children.length - 1].getTextRange().getEndOffset();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (child instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)child;

        if (token.getTokenType() == JavaTokenType.RBRACE) {
          int j = i - 1;

          while (children[j] instanceof PsiWhiteSpace && children[j].getTextRange().getStartOffset() > startOffset) {
            j--;
          }

          end = children[j].getTextRange().getEndOffset();
        }
      }
    }
    return end;
  }
}
