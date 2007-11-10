package com.intellij.openapi.command.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;

/**
 * @author max
 */
class FocusBasedCurrentEditorProvider implements CurrentEditorProvider {
  public FileEditor getCurrentEditor() {
    return PlatformDataKeys.FILE_EDITOR.getData(DataManager.getInstance().getDataContext());
  }
}