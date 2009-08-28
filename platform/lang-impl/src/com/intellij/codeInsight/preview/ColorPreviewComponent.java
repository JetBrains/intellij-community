package com.intellij.codeInsight.preview;

import javax.swing.*;
import java.awt.*;

/**
 * @author spleaner
 */
public class ColorPreviewComponent extends JComponent {
  private final Color myColor;

  public ColorPreviewComponent(final String hexValue, final Color color) {
    myColor = color;
    setOpaque(true);

/*    if (hexValue != null) {
      final JLabel label = new JLabel('#' + hexValue);
      label.setFont(UIUtil.getToolTipFont());
      label.setForeground(Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[2] >= 0.5f ? Color.BLACK : Color.WHITE);
      add(label, BorderLayout.SOUTH);
    } */
  }

  public void paintComponent(final Graphics g) {
    final Graphics2D g2 = (Graphics2D)g;

    final Rectangle r = getBounds();

    g2.setPaint(myColor);
    g2.fillRect(1, 1, r.width - 2, r.height - 2);

    g2.setPaint(Color.BLACK);
    g2.drawRect(0, 0, r.width - 1, r.height - 1);
  }

  public Dimension getPreferredSize() {
    return new Dimension(70, 25);
  }
}
