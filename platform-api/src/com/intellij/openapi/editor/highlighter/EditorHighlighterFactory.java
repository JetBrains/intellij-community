package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.editor.colors.EditorColorsScheme;

/**
 * @author yole
 */
public abstract class EditorHighlighterFactory {

  public static EditorHighlighterFactory getInstance() {
    return ServiceManager.getService(EditorHighlighterFactory.class);
  }

  public abstract EditorHighlighter createEditorHighlighter(final SyntaxHighlighter syntaxHighlighter, final EditorColorsScheme colors);
}