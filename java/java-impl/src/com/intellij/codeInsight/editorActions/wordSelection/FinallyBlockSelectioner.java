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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;
import java.util.ArrayList;

public class FinallyBlockSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiKeyword && PsiKeyword.FINALLY.equals(e.getText());
  }


  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = new ArrayList<>();

    final PsiElement parent = e.getParent();
    if (parent instanceof PsiTryStatement) {
      final PsiTryStatement tryStatement = (PsiTryStatement)parent;
      final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock != null) {
        result.add(new TextRange(e.getTextRange().getStartOffset(), finallyBlock.getTextRange().getEndOffset()));
      }
    }

    return result;
  }
}
