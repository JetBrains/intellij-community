package com.intellij.ide;

import com.intellij.openapi.fileEditor.FileEditor;

public interface FileEditorProvider {
  FileEditor openFileEditor();
}
