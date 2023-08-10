// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import org.jetbrains.annotations.NotNull;

import java.util.EventObject;

public class EditorFactoryEvent extends EventObject {
  private final Editor myEditor;

  public EditorFactoryEvent(@NotNull EditorFactory editorFactory, @NotNull Editor editor) {
    super(editorFactory);
    myEditor = editor;
  }

  public @NotNull EditorFactory getFactory(){
    return (EditorFactory) getSource();
  }

  public @NotNull Editor getEditor() {
    return myEditor;
  }
}
