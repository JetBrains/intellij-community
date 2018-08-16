package com.intellij.json;

import com.intellij.codeInsight.editorActions.MultiCharQuoteHandler;
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.json.editor.JsonTypedHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.PsiFile;
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
    return "\"";
  }

  @Override
  public void insertClosingQuote(@NotNull Editor editor, int offset, PsiFile file, @NotNull CharSequence closingQuote) {
    editor.getDocument().insertString(offset, closingQuote);
    JsonTypedHandler.processPairedBracesComma('"', editor, file);
  }
}
