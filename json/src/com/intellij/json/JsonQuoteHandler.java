// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json;

import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler;
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.json.editor.JsonTypedHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonQuoteHandler extends SimpleTokenSetQuoteHandler implements MultiCharQuoteHandler {
  public JsonQuoteHandler() {
    super(JsonParserDefinition.STRING_LITERALS);
  }

  @Nullable
  @Override
  public CharSequence getClosingQuote(@NotNull HighlighterIterator iterator, int offset) {
    final IElementType tokenType = iterator.getTokenType();
    if (tokenType == TokenType.WHITE_SPACE) {
      final int index = iterator.getStart() - 1;
      if (index >= 0) {
        return String.valueOf(iterator.getDocument().getCharsSequence().charAt(index));
      }
    }
    return tokenType == JsonElementTypes.SINGLE_QUOTED_STRING ? "'" : "\"";
  }

  @Override
  public void insertClosingQuote(@NotNull Editor editor, int offset, @NotNull PsiFile file, @NotNull CharSequence closingQuote) {
    editor.getDocument().insertString(offset, closingQuote);
    JsonTypedHandler.processPairedBracesComma(closingQuote.charAt(0), editor, file);
  }
}