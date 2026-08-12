// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.panels;

import com.intellij.openapi.util.UtilThreadingAssertions;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.LayoutManager;

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

    UtilThreadingAssertions.softAssertAwtOperationsThread();
  }

  @Override
  protected void paintComponent(Graphics g) {
    Color bg = getBackground();
    g.setColor(bg);
    Dimension size = getSize();
    g.fillRect(0, 0, size.width, size.height);
  }

  public static class List extends OpaquePanel {
    public List() {
    }

    public List(LayoutManager layout) {
      super(layout);
    }

    public List(Color color) {
      super(color);
    }

    public List(LayoutManager layoutManager, Color color) {
      super(layoutManager, color);
    }
  }
}
