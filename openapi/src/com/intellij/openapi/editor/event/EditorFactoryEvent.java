/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;

import java.util.EventObject;

public class EditorFactoryEvent extends EventObject {
  private final Editor myEditor;

  public EditorFactoryEvent(EditorFactory editorFactory, Editor editor) {
    super(editorFactory);
    myEditor = editor;
  }

  public EditorFactory getFactory(){
    return (EditorFactory) getSource();
  }

  public Editor getEditor() {
    return myEditor;
  }
}
