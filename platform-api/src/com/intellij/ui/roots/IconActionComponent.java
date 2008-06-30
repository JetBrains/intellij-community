package com.intellij.ui.roots;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 3
 * @author 2003
 */
public class IconActionComponent extends ScalableIconComponent {
  public IconActionComponent(Icon icon, Icon rolloverIcon, String tooltipText, final Runnable action) {
    super(icon, rolloverIcon);
    this.addMouseListener(new MouseAdapter() {
      public void mouseEntered(MouseEvent e) {
        setSelected(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }

      public void mouseClicked(MouseEvent e) {
        if (action != null) {
          action.run();
        }
      }

      public void mouseExited(MouseEvent e) {
        setSelected(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    });
    if (tooltipText != null) {
      this.setToolTipText(tooltipText);
    }
  }

}
