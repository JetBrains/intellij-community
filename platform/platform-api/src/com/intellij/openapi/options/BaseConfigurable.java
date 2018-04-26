// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  // defined here for backward-compatibility
  public JComponent getPreferredFocusedComponent() {
    return null;
  }
}