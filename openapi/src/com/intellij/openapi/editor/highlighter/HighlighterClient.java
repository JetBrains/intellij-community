package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;

/**
 * @author max
 */
public interface HighlighterClient {
  Project getProject();

  void repaint(int start, int end);

  Document getDocument();
}
