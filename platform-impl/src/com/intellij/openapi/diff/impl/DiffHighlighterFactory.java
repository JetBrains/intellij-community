package com.intellij.openapi.diff.impl;

import com.intellij.openapi.editor.highlighter.EditorHighlighter;

public interface DiffHighlighterFactory {
  EditorHighlighter createHighlighter();
}
