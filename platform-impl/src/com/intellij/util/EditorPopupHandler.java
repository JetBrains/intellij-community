package com.intellij.util;

import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;

public abstract class EditorPopupHandler extends EditorMouseAdapter {
  public abstract void invokePopup(EditorMouseEvent event);

  private void handle(EditorMouseEvent e) {
    if (e.getMouseEvent().isPopupTrigger() && e.getArea() == EditorMouseEventArea.EDITING_AREA) {
      invokePopup(e);
      e.consume();
    }
  }

  public void mouseClicked(EditorMouseEvent e) {
    handle(e);
  }

  public void mousePressed(EditorMouseEvent e) {
    handle(e);
  }

  public void mouseReleased(EditorMouseEvent e) {
    handle(e);
  }
}