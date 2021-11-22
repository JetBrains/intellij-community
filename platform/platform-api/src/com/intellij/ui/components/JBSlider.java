// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import javax.swing.*;

public class JBSlider extends JSlider {

  public JBSlider() {
  }

  public JBSlider(int orientation) {
    super(orientation);
  }

  public JBSlider(int min, int max) {
    super(min, max);
  }

  public JBSlider(int min, int max, int value) {
    super(min, max, value);
  }

  public JBSlider(int orientation, int min, int max, int value) {
    super(orientation, min, max, value);
  }

  public JBSlider(BoundedRangeModel brm) {
    super(brm);
  }

  private boolean fireEvents = true;

  @Override
  protected void fireStateChanged() {
    if (fireEvents) {
      super.fireStateChanged();
    }
  }

  public final void setValueWithoutEvents(int n) {
    fireEvents = false;
    try {
      setValue(n);
    }
    finally {
      fireEvents = true;
    }
  }
}
