/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.highlighter.custom.impl;

import com.intellij.codeInsight.editorActions.BraceMatcherBasedSelectioner;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class CustomFileTypeSelectWordHandler extends BraceMatcherBasedSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e.getContainingFile().getFileType() instanceof CustomSyntaxTableFileType;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> superResult = super.select(e, editorText, cursorOffset, editor);

    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(cursorOffset);
    if (CustomFileTypeQuoteHandler.isQuotedToken(iterator.getTokenType())) {
      List<TextRange> result = ContainerUtil.newArrayList();
      int start = iterator.getStart();
      int end = iterator.getEnd();
      if (end - start > 2) {
        result.add(new TextRange(start + 1, end - 1));
      }
      result.add(new TextRange(start, end));
      if (superResult != null) {
        result.addAll(superResult);
      }
      return result;
    }
    
    return superResult;
  }
}
