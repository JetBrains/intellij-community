/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;

import java.util.EventObject;

public class CaretEvent extends EventObject {
  private final LogicalPosition myOldPosition;
  private final LogicalPosition myNewPosition;

  public CaretEvent(Editor editor, LogicalPosition oldPosition, LogicalPosition newPosition) {
    super(editor);
    myOldPosition = oldPosition;
    myNewPosition = newPosition;
  }

  public Editor getEditor() {
    return (Editor) getSource();
  }

  public LogicalPosition getOldPosition() {
    return myOldPosition;
  }

  public LogicalPosition getNewPosition() {
    return myNewPosition;
  }
}
