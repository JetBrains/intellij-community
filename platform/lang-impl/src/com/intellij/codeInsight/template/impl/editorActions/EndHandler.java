package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

/**
 * @author yole
 */
public class EndHandler extends HomeEndHandler {
  public EndHandler(final EditorActionHandler originalHandler) {
    super(originalHandler, false);
  }
}