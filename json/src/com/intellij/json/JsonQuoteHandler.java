// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler;
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.json.editor.JsonTypedHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.json.JsonTokenSets.STRING_LITERALS;

/**
 * @author Mikhail Golubev
 */
public final class JsonQuoteHandler extends SimpleTokenSetQuoteHandler implements MultiCharQuoteHandler {
  public JsonQuoteHandler() {
    super(STRING_LITERALS);
  }

  @Override
  public @Nullable CharSequence getClosingQuote(@NotNull HighlighterIterator iterator, int offset) {
    if (offset == 0) {
      // there can be no open string literal at zero offset
      return null;
    }

    if (iterator.getStart() == offset) {
      iterator.retreat();
    }

    // ensure we're right after opening quote
    IElementType tokenType = iterator.getTokenType();
    if (tokenType != JsonElementTypes.SINGLE_QUOTED_STRING &&
        tokenType != JsonElementTypes.DOUBLE_QUOTED_STRING) {
      return null;
    }

    if (offset != iterator.getStart() + 1) {
      return null;
    }

    if (previousTokenIsValidEscapeSequence(iterator)) {
      // aha, the string is actually longer, aborting!
      return null;
    }

    if (tokenType == JsonElementTypes.DOUBLE_QUOTED_STRING) {
      return "\"";
    }
    else {
      return "'";
    }
  }

  @Override
  public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
    return super.isOpeningQuote(iterator, offset) && !previousTokenIsValidEscapeSequence(iterator);
  }

  @Override
  public void insertClosingQuote(@NotNull Editor editor, int offset, @NotNull PsiFile file, @NotNull CharSequence closingQuote) {
    insertClosingQuote(editor, offset, closingQuote);
    JsonTypedHandler.processPairedBracesComma(closingQuote.charAt(0), editor, file);
  }

  /**
   * we're using highlighting iterator, and a valid escape sequence can "break" a string token into several highlighting tokens.
   * let's check that. if that's the case, we are not at the start of just open string literal, and we have to return false
   * {@code "abc\""<caret>}, where the last quote has been just typed in.
   */
  private static boolean previousTokenIsValidEscapeSequence(@NotNull HighlighterIterator iterator) {
    if (iterator.getStart() > 0) {
      iterator.retreat();
      IElementType prev = iterator.getTokenType();
      if (prev == StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN) {
        return true;
      }
    }
    return false;
  }
}