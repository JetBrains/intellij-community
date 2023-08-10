// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

public class CaretEvent extends EventObject {
  private final Caret myCaret;
  private final LogicalPosition myOldPosition;
  private final LogicalPosition myNewPosition;

  public CaretEvent(@NotNull Caret caret, @NotNull LogicalPosition oldPosition, @NotNull LogicalPosition newPosition) {
    super(caret.getEditor());
    myCaret = caret;
    myOldPosition = oldPosition;
    myNewPosition = newPosition;
  }

  public @NotNull Editor getEditor() {
    return (Editor) getSource();
  }

  public @Nullable Caret getCaret() {
    return myCaret;
  }

  public @NotNull LogicalPosition getOldPosition() {
    return myOldPosition;
  }

  public @NotNull LogicalPosition getNewPosition() {
    return myNewPosition;
  }
}
