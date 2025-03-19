// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

/**
 * Convenience class to listen for double clicks of the primary mouse button.
 * @author max
 */
public abstract class DoubleClickListener extends ClickListener {
  @Override
  public final boolean onClick(@NotNull MouseEvent event, int clickCount) {
    if (clickCount == 2 && event.getButton() == MouseEvent.BUTTON1) {
      return onDoubleClick(event);
    }
    return false;
  }

  /**
   * Handle left/primary/first button double click.
   *
   * @param event the mouse click event.
   * @return true, if the event is handled (and should be consumed). false if the event should be passed on to other listeners.
   */
  protected abstract boolean onDoubleClick(@NotNull MouseEvent event);
}
