// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.ui.components.panels.Wrapper;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

public class InvisibleWrapper extends Wrapper {
  private final ComponentListener myListener = new MyVisibilityListener();

  @Override
  public void setContent(JComponent wrapped) {
    if (getTargetComponent() != this) {
      getTargetComponent().removeComponentListener(myListener);
    }

    super.setContent(wrapped);

    if (getTargetComponent() != this) {
      getTargetComponent().addComponentListener(myListener);
    }
    syncVisibility();
  }

  private void syncVisibility() {
    JComponent target = getTargetComponent();
    boolean isVisible = target != this && target.isVisible();
    if (isVisible() != isVisible) {
      setVisible(isVisible);
      validate();
    }
  }

  private class MyVisibilityListener extends ComponentAdapter {
    @Override
    public void componentShown(ComponentEvent e) {
      syncVisibility();
    }

    @Override
    public void componentHidden(ComponentEvent e) {
      syncVisibility();
    }
  }
}
