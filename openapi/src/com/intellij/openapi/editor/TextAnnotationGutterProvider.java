package com.intellij.openapi.editor;

import com.intellij.openapi.editor.Editor;

/**
 * @author max
 */
public interface TextAnnotationGutterProvider {
  String getLineText(int line, Editor editor);
  void gutterClosed();
}
