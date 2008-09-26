package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

/**
 * @author yole
 */
public class HomeHandler extends HomeEndHandler {
  public HomeHandler(final EditorActionHandler originalHandler) {
    super(originalHandler, true);
  }
}
