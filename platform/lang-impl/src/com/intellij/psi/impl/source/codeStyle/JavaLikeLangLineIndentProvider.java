/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.formatting.Indent;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A base class Java-like language line indent provider. If JavaLikeLangLineIndentProvider is unable to calculate
 * the indentation, it forwards the request to FormatterBasedLineIndentProvider.
 */
public abstract class JavaLikeLangLineIndentProvider extends FormatterBasedLineIndentProvider {
  @Nullable
  @Override
  public String getLineIndent(@NotNull Project project, @NotNull Editor editor, @NotNull Document document, int offset) {
    Indent.Type indent = getIndent(editor, document, offset);
    if (indent == Indent.Type.NONE) return null;
    return super.getLineIndent(project, editor, document, offset);
  }

  @Override
  public final boolean isSuitableFor(@Nullable PsiFile file) {
    if (file != null) {
      return isSuitableForLanguage(file.getLanguage());
    }
    return false;
  }
  
  protected abstract boolean isSuitableForLanguage(@NotNull Language language);
  
  @Nullable
  protected Indent.Type getIndent(@NotNull Editor editor, @NotNull Document document, int offset) {
    CharSequence docChars = document.getCharsSequence();
    EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 1);
    if (isWhitespace(iterator.getTokenType())) {
      if (containsLineBreaks(iterator, docChars)) {
        iterator.retreat();
        if (!iterator.atEnd()) {
          if (isEndOfCodeBlock(iterator)) return Indent.Type.NONE;
        }
      }
    }
    return null;
  }
  
  protected abstract boolean isWhitespace(@NotNull IElementType tokenType);
  
  private static boolean containsLineBreaks(@NotNull HighlighterIterator iterator, @NotNull CharSequence chars) {
    return CharArrayUtil.containLineBreaks(chars, iterator.getStart(), iterator.getEnd());
  }
  
  protected boolean isEndOfCodeBlock(@NotNull HighlighterIterator iterator) {
    if (isSemicolon(iterator.getTokenType())) iterator.retreat();
    if (!iterator.atEnd()) {
      if (isWhitespace(iterator.getTokenType())) iterator.retreat();
      if (!iterator.atEnd()) {
        return isBlockClosingBrace(iterator.getTokenType());
      }
    }
    return false;
  }
  
  protected abstract boolean isSemicolon(@NotNull IElementType tokenType);
  
  protected abstract boolean isBlockClosingBrace(@NotNull IElementType tokenType);
}
