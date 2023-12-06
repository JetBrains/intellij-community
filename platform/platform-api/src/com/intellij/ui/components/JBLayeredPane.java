// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.awt.*;

public class JBLayeredPane extends JLayeredPane {
  private boolean myFullOverlayLayout;

  @Override
  public Component add(Component comp, int index) {
    Logger.getInstance(JBLayeredPane.class)
      .warn("Probably incorrect call - constraint as primitive integer will be used as index", new Throwable());
    addImpl(comp, null, index);
    return comp;
  }

  @Override
  public Dimension getMinimumSize() {
    if (!isMinimumSizeSet())
      return new Dimension(0, 0);
    return super.getMinimumSize();
  }

  /**
   * @see #setFullOverlayLayout(boolean)
   */
  public boolean isFullOverlayLayout() {
    return myFullOverlayLayout;
  }

  /**
   * When enabled, all pane's layers are resized to match pane's size at layout.
   * This will override the effect from setting any layout manager.
   */
  public void setFullOverlayLayout(boolean enabled) {
    myFullOverlayLayout = enabled;
  }

  @Override
  public void doLayout() {
    if (isFullOverlayLayout()) {
      int width = getWidth();
      int height = getHeight();
      for (int i = getComponentCount() - 1; i >= 0; i--) {
        getComponent(i).setBounds(0, 0, width, height);
      }
    } else {
      super.doLayout();
    }
  }
}
