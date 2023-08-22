// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.EventObject;

public final class VisibleAreaEvent extends EventObject {
  private final Rectangle myOldRectangle;
  private final Rectangle myNewRectangle;

  public VisibleAreaEvent(@NotNull Editor editor, Rectangle oldViewRectangle, @NotNull Rectangle newViewRectangle) {
    super(editor);
    myNewRectangle = newViewRectangle;
    myOldRectangle = oldViewRectangle;
  }

  public @NotNull Editor getEditor() {
    return (Editor) getSource();
  }

  public Rectangle getOldRectangle() {
    return myOldRectangle;
  }

  public @NotNull Rectangle getNewRectangle() {
    return myNewRectangle;
  }
}
