/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Editor;

import java.awt.event.MouseEvent;
import java.util.EventObject;

public class EditorMouseEvent extends EventObject {

  private final MouseEvent myMouseEvent;
  private final EditorMouseEventArea myEditorArea;

  public EditorMouseEvent(Editor editor, MouseEvent mouseEvent, EditorMouseEventArea area) {
    super(editor);

    myMouseEvent = mouseEvent;
    myEditorArea = area;
  }

  public Editor getEditor() {
    return (Editor) getSource();
  }

  public MouseEvent getMouseEvent() {
    return myMouseEvent;
  }

  public void consume() {
    myMouseEvent.consume();
  }

  public boolean isConsumed() {
    return myMouseEvent.isConsumed();
  }

  public EditorMouseEventArea getArea() {
    return myEditorArea;
  }
}
