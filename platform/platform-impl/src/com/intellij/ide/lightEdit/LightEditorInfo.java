// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

public class LightEditorInfo {

  private final Editor myEditor;
  private final VirtualFile myFile;

  LightEditorInfo(@NotNull Editor editor, @NotNull VirtualFile file) {
    myEditor = editor;
    myFile = file;
  }

  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  public boolean isUnsaved() {
    if (isNew()) {
      return true;
    }
    else {
      return FileDocumentManager.getInstance().isFileModified(myFile);
    }
  }

  public boolean isNew() {
    return myFile instanceof LightVirtualFile;
  }

}
