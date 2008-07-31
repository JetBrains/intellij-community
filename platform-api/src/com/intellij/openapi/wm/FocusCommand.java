package com.intellij.openapi.wm;

import com.intellij.openapi.util.ActionCallback;

public abstract class FocusCommand extends ActionCallback.Runnable {
  protected FocusCommand() {
  }

  protected FocusCommand(final Object object) {
    super(object);
  }

  protected FocusCommand(final Object[] objects) {
    super(objects);
  }

  public boolean isExpired() {
    return false;
  }
}