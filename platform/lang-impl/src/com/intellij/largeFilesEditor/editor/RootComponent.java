// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

public class RootComponent {
  private final EditorManager editorManager;


  public RootComponent(EditorManager editorManager) {
    this.editorManager = editorManager;
  }


  public EditorManager getEditorManager() {
    return editorManager;
  }
}
