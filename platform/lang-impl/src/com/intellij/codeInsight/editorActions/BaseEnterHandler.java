package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;

public abstract class BaseEnterHandler extends EditorWriteActionHandler {
  private static final String GROUP_ID = "EnterHandler.GROUP_ID";

  @Override
  public DocCommandGroupId getCommandGroupId(Editor editor) {
    return DocCommandGroupId.withGroupId(editor.getDocument(), GROUP_ID);
  }
}
