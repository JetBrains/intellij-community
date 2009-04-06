package com.intellij.notification.impl.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.*;

/**
 * @author spleaner
 */
public class StickyButtonUI extends BasicToggleButtonUI {
  public static final float FONT_SIZE = 11.0f;

  @Override
  protected void installDefaults(final AbstractButton b) {
    super.installDefaults(b);
    b.setFont(UIManager.getFont("Button.font").deriveFont(Font.BOLD, FONT_SIZE));
  }

  @Override
  public void paint(final Graphics g, final JComponent c) {
    AbstractButton button = (AbstractButton) c;

    final int width = button.getWidth();
    final int height = button.getHeight();

    final Graphics2D g2 = (Graphics2D) g.create();

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    final ButtonModel model = button.getModel();
    if (model.isSelected()) {
      g2.setColor(Color.GRAY);
      g2.fillRoundRect(0, 0, width - 1, height - 1, 10, 10);
    } else if (model.isRollover()) {
      g2.setColor(Color.LIGHT_GRAY);
      g2.fillRoundRect(0, 0, width - 1, height - 1, 10, 10);
    }

    if (button.hasFocus()) {
      g2.setColor(new Color(100, 100, 100));
      g2.drawRoundRect(0, 0, width - 1, height - 1, 10, 10);
    }

    g2.dispose();
    super.paint(g, c);
  }

}
