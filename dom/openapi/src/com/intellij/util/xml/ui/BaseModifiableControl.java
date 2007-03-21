/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;

/**
 * @author peter
 */
public abstract class BaseModifiableControl<Bound extends JComponent, T> extends BaseControl<Bound,T> {
  private boolean myModified;

  protected BaseModifiableControl(final DomWrapper<T> domWrapper) {
    super(domWrapper);
  }

  protected final void setModified() {
    myModified = true;
  }

  protected void doCommit(final T value) throws IllegalAccessException, InvocationTargetException {
    super.doCommit(value);
    myModified = false;
  }

  protected boolean isCommitted() {
    return !myModified;
  }
}
