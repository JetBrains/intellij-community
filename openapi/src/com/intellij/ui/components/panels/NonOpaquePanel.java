/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.components.panels;

import javax.swing.*;
import java.awt.*;

public class NonOpaquePanel extends Wrapper {

  public NonOpaquePanel() {
    setOpaque(false);
  }

  public NonOpaquePanel(JComponent wrapped) {
    super(wrapped);
    setOpaque(false);
  }

  public NonOpaquePanel(LayoutManager layout, JComponent wrapped) {
    super(layout, wrapped);
    setOpaque(false);
  }

  public NonOpaquePanel(boolean isDoubleBuffered) {
    super(isDoubleBuffered);
    setOpaque(false);
  }

  public NonOpaquePanel(LayoutManager layout) {
    super(layout);
    setOpaque(false);
  }

  public NonOpaquePanel(LayoutManager layout, boolean isDoubleBuffered) {
    super(layout, isDoubleBuffered);
    setOpaque(false);
  }

}
