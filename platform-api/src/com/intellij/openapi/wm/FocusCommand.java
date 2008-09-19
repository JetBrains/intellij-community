package com.intellij.openapi.wm;

import com.intellij.openapi.util.ActiveRunnable;

public abstract class FocusCommand extends ActiveRunnable {
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