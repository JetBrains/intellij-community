/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.components.panels;


import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Belyaev
 */
public class OpaqueWrapper extends Wrapper {

  public OpaqueWrapper(JComponent wrapped, Color color) {
    super(wrapped);
    setBackground(color);
  }

  public OpaqueWrapper(LayoutManager layoutManager, JComponent wrapped, Color color) {
    super(layoutManager, wrapped);
    setBackground(color);
  }

  protected void paintComponent(Graphics g) {
    g.setColor(getBackground());
    Dimension size = getSize();
    g.fillRect(0, 0, size.width, size.height);
  }
}
