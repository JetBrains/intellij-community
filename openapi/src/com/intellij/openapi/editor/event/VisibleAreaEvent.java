/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Editor;

import java.awt.*;
import java.util.EventObject;

public class VisibleAreaEvent extends EventObject {
  private final Rectangle myOldRectangle;
  private final Rectangle myNewRectangle;

  public VisibleAreaEvent(Editor editor, Rectangle oldViewRectangle, Rectangle newViewRectangle) {
    super(editor);
    myNewRectangle = newViewRectangle;
    myOldRectangle = oldViewRectangle;
  }

  public Editor getEditor() {
    return (Editor) getSource();
  }

  public Rectangle getOldRectangle() {
    return myOldRectangle;
  }

  public Rectangle getNewRectangle() {
    return myNewRectangle;
  }
}
