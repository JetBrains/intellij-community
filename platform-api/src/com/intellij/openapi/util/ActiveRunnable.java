package com.intellij.openapi.util;

import com.intellij.util.ui.update.ComparableObject;

public abstract class ActiveRunnable extends ComparableObject.Impl {

  protected ActiveRunnable() {
  }

  protected ActiveRunnable(final Object object) {
    super(object);
  }

  protected ActiveRunnable(final Object[] objects) {
    super(objects);
  }

  public abstract ActionCallback run();
}