/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.options;

import javax.swing.*;


public abstract class BaseConfigurable implements Configurable {
  protected boolean myModified;

  public boolean isModified() {
    return myModified;
  }

  protected void setModified(final boolean modified) {
    myModified = modified;
  }

  /**
   * @return component which should be focused when the dialog appears
   *         on the screen.
   */
  public JComponent getPreferredFocusedComponent() {
    return null;
  }
}