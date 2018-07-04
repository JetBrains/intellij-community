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

import com.intellij.psi.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

public class ListSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiParameterList || e instanceof PsiExpressionList;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {

    PsiElement[] children = e.getChildren();

    int start = 0;
    int end = 0;

    for (PsiElement child : children) {
      if (child instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)child;

        if (token.getTokenType() == JavaTokenType.LPARENTH) {
          start = token.getTextOffset() + 1;
        }
        if (token.getTokenType() == JavaTokenType.RPARENTH) {
          end = token.getTextOffset();
        }
      }
    }

    List<TextRange> result = new ArrayList<>();
    if (start != 0 && end != 0) {
      result.add(new TextRange(start, end));
    }
    return result;
  }
}
