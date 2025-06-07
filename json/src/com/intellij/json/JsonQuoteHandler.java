// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler;
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.json.editor.JsonTypedHandler;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.json.JsonTokenSets.STRING_LITERALS;
import static com.intellij.json.split.JsonBackendExtensionSuppressorKt.shouldDoNothingInBackendMode;

/**
 * @author Mikhail Golubev
 */
public final class JsonQuoteHandler extends SimpleTokenSetQuoteHandler implements MultiCharQuoteHandler {
  public JsonQuoteHandler() {
    super(STRING_LITERALS);
  }

  @Override
  public @Nullable CharSequence getClosingQuote(@NotNull HighlighterIterator iterator, int offset) {
    if (shouldDoNothingInBackendMode()) return null;

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
    PsiElement element = file.findElementAt(offset - 1);
    PsiElement parent = element == null ? null : element.getParent();
    if (parent instanceof JsonStringLiteral) {
      PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
      TextRange range = parent.getTextRange();
      if (offset - 1 != range.getStartOffset() || !"\"".contentEquals(closingQuote)) {
        int endOffset = range.getEndOffset();
        if (offset < endOffset) return;
        if (offset == endOffset && !StringUtil.isEmpty(((JsonStringLiteral)parent).getValue())) return;
      }
    }
    editor.getDocument().insertString(offset, closingQuote);
    JsonTypedHandler.processPairedBracesComma(closingQuote.charAt(0), editor, file);
  }
}