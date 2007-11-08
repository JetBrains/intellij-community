package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class IdeaActionButtonLook extends ActionButtonLook {
  public void paintBackground(Graphics g, JComponent component, int state) {
    Dimension dimension = component.getSize();
    if (state != ActionButtonComponent.NORMAL) {
      if (state == ActionButtonComponent.POPPED) {
        g.setColor(new Color(181, 190, 214));
        g.fillRect(0, 0, dimension.width, dimension.height);
      }
      else {
        g.setColor(new Color(130, 146, 185));
        g.fillRect(0, 0, dimension.width, dimension.height);
      }
    }
    if (state == ActionButtonComponent.PUSHED) {
      g.setColor(new Color(130, 146, 185));
      g.fillRect(0, 0, dimension.width, dimension.height);
    }
  }

  public void paintBorder(Graphics g, JComponent component, int state) {
    if (state == ActionButtonComponent.NORMAL) return;
    Rectangle rectangle = new Rectangle(component.getWidth(), component.getHeight());
    Color color = new Color(8, 36, 107);
    g.setColor(color);
    UIUtil.drawLine(g, rectangle.x, rectangle.y, rectangle.x, (rectangle.y + rectangle.height) - 1);
    UIUtil.drawLine(g, rectangle.x, rectangle.y, (rectangle.x + rectangle.width) - 1, rectangle.y);
    UIUtil.drawLine(g, (rectangle.x + rectangle.width) - 1, rectangle.y, (rectangle.x + rectangle.width) - 1,
                    (rectangle.y + rectangle.height) - 1);
    UIUtil.drawLine(g, rectangle.x, (rectangle.y + rectangle.height) - 1, (rectangle.x + rectangle.width) - 1,
                    (rectangle.y + rectangle.height) - 1);
  }

  public void paintIcon(Graphics g, ActionButtonComponent actionButton, Icon icon) {
    int width = icon.getIconWidth();
    int height = icon.getIconHeight();
    int x = (int)Math.ceil((actionButton.getWidth() - width) / 2);
    int y = (int)Math.ceil((actionButton.getHeight() - height) / 2);
    paintIconAt(g, actionButton, icon, x, y);
  }

  public void paintIconAt(Graphics g, ActionButtonComponent button, Icon icon, int x, int y) {
    if (button.getPopState() == ActionButtonComponent.PUSHED) {
      x++;
      y++;
    }
    icon.paintIcon(null, g, x, y);
  }
}
