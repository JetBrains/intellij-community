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
public class OpaquePanel extends JPanel {

  public OpaquePanel() {
    this(null, null);
  }

  public OpaquePanel(LayoutManager layout) {
    this(layout, null);
  }

  public OpaquePanel(Color color) {
    this(null, color);
  }

  public OpaquePanel(LayoutManager layoutManager, Color color) {
    super(layoutManager);
    setBackground(color);
  }

  protected void paintComponent(Graphics g) {
    g.setColor(getBackground());
    Dimension size = getSize();
    g.fillRect(0, 0, size.width, size.height);
  }
}
