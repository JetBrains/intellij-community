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

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiJavaToken;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ForStatementHeaderSelectioner implements ExtendWordSelectionHandler {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiForStatement || e instanceof PsiForeachStatement;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    PsiJavaToken lParen = e instanceof PsiForStatement ? ((PsiForStatement)e).getLParenth() 
                                                       : e instanceof PsiForeachStatement ? ((PsiForeachStatement)e).getLParenth() : null;
    PsiJavaToken rParen = e instanceof PsiForStatement ? ((PsiForStatement)e).getRParenth() 
                                                       : e instanceof PsiForeachStatement ? ((PsiForeachStatement)e).getRParenth() : null;
    if (lParen == null || rParen == null) return null;
    TextRange result = new TextRange(lParen.getTextRange().getEndOffset(), rParen.getTextRange().getStartOffset());
    return result.containsOffset(cursorOffset) ? Collections.singletonList(result) : null;
  }
}
