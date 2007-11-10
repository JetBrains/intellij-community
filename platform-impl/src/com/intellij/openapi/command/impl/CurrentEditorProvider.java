package com.intellij.openapi.command.impl;

import com.intellij.openapi.fileEditor.FileEditor;

/**
 * @author max
 */
public interface CurrentEditorProvider {
  FileEditor getCurrentEditor();
}
