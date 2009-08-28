package com.intellij.openapi.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class SimpleToolWindowPanel extends JPanel {

  private JComponent myToolbar;
  private JComponent myContent;

  private boolean myBorderless;
  private boolean myVertical;

  public SimpleToolWindowPanel(boolean vertical) {
    this(vertical, false);
  }

  public SimpleToolWindowPanel(boolean vertical, boolean borderless) {
    setLayout(new BorderLayout(vertical ? 0 : 1, vertical ? 1 : 0));
    myBorderless = borderless;
    myVertical = vertical;
  }

  public void setToolbar(JComponent c) {
    myToolbar = c;

    if (myVertical) {
      add(c, BorderLayout.NORTH);
    } else {
      add(c, BorderLayout.WEST);
    }

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
      if (myVertical) {
        final int y = (int)myToolbar.getBounds().getMaxY();
        g.drawLine(0, y, getWidth(), y);
      } else {
        int x = (int)myToolbar.getBounds().getMaxX();
        g.drawLine(x, 0, x, getHeight());
      }
    }
  }
}