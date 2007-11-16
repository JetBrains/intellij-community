package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;

/**
 * @author yole
 */
public class EditorHighlighterFactoryImpl extends EditorHighlighterFactory {
  public EditorHighlighter createEditorHighlighter(SyntaxHighlighter highlighter, final EditorColorsScheme colors) {
    if (highlighter == null) highlighter = new PlainSyntaxHighlighter();
    return new LexerEditorHighlighter(highlighter, colors);
  }
}