package com.intellij.ui;

import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * @author Eugene Belyaev
 */
public class RoundedLineBorder extends LineBorder {
  private int myArcSize = 1;

  public RoundedLineBorder(Color color) {
    super(color);
  }

  public RoundedLineBorder(Color color, int arcSize) {
    this(color, arcSize, 1);
  }

  public RoundedLineBorder(Color color, int arcSize, final int thickness) {
    super(color, thickness);
    myArcSize = arcSize;
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    final Graphics2D g2 = (Graphics2D)g;

    final Object oldAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    final Color oldColor = g2.getColor();
    g2.setColor(lineColor);

    for (int i = 0; i < thickness; i++) {
      g2.drawRoundRect(x + i, y + i, width - i - i - 1, height - i - i - 1, myArcSize, myArcSize);
    }

    g2.setColor(oldColor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);
  }
}
