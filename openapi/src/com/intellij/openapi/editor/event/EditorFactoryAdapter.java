/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.event;

/**
 * @author max
 */
public abstract class EditorFactoryAdapter implements EditorFactoryListener {
  public void editorCreated(EditorFactoryEvent event) {
  }

  public void editorReleased(EditorFactoryEvent event) {
  }
}
