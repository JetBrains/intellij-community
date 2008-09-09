package com.intellij.openapi.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class SimpleToolWindowPanel extends JPanel {

  private JComponent myToolbar;
  private JComponent myContent;

  private boolean myBorderless;

  public SimpleToolWindowPanel() {
    this(false);
  }

  public SimpleToolWindowPanel(boolean borderless) {
    setLayout(new BorderLayout(0, 1));
    myBorderless = borderless;
  }

  public void setToolbar(JComponent c) {
    myToolbar = c;
    add(c, BorderLayout.NORTH);

    if (myBorderless) {
      UIUtil.removeScrollBorder(c);
    }

    revalidate();
    repaint();
  }

  public void setContent(JComponent c) {
    myContent = c;
    add(c, BorderLayout.CENTER);

    if (myBorderless) {
      UIUtil.removeScrollBorder(c);
    }

    revalidate();
    repaint();
  }

  @Override
  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);

    if (myToolbar != null && myToolbar.getParent() == this && myContent != null && myContent.getParent() == this) {
      g.setColor(UIUtil.getBorderSeparatorColor());
      final int y = (int)myToolbar.getBounds().getMaxY();
      g.drawLine(0, y, getWidth(), y);
    }
  }
}