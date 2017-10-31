/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.util.ui.JBInsets;
import sun.swing.MenuItemLayoutHelper;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicMenuItemUI;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaMenuItemUIBase extends BasicMenuItemUI {
  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
      return new DarculaMenuItemUIBase();
  }

  public void processMouseEvent(JMenuItem item, MouseEvent e, MenuElement[] path, MenuSelectionManager manager) {
    Point p = e.getPoint();
    if (p.x >= 0 && p.x < item.getWidth() &&
        p.y >= 0 && p.y < item.getHeight()) {
      if (e.getID() == MouseEvent.MOUSE_RELEASED) {
        manager.clearSelectedPath();
        item.doClick(0);
        item.setArmed(false);
      } else
        manager.setSelectedPath(path);
    } else if (item.getModel().isArmed()) {
      MenuElement[] newPath = new MenuElement[path.length - 1];
      int i, c;
      for (i = 0, c = path.length - 1; i < c; i++)
        newPath[i] = path[i];
      manager.setSelectedPath(newPath);
    }
  }

  protected void paintMenuItem(Graphics g, JComponent c,
                                   Icon checkIcon, Icon arrowIcon,
                                   Color background, Color foreground,
                                   int defaultTextIconGap) {
    // Save original graphics font and color
    Font holdf = g.getFont();
    Color holdc = g.getColor();

    JMenuItem mi = (JMenuItem) c;
    g.setFont(mi.getFont());

    Rectangle viewRect = new Rectangle(0, 0, mi.getWidth(), mi.getHeight());
    JBInsets.removeFrom(viewRect, mi.getInsets());

    MenuItemLayoutHelper lh = new MenuItemLayoutHelper(mi, checkIcon,
        arrowIcon, viewRect, defaultTextIconGap, "-", //todo[kb] use protected field BasicMenuItemUI.acceleratorDelimiter when we move to java 1.7
        mi.getComponentOrientation().isLeftToRight(), mi.getFont(),
        acceleratorFont, MenuItemLayoutHelper.useCheckAndArrow(menuItem),
        getPropertyPrefix());
    MenuItemLayoutHelper.LayoutResult lr = lh.layoutMenuItem();

    paintBackground(g, mi, background);
    paintCheckIcon(g, lh, lr, holdc, foreground);
    paintIcon(g, lh, lr, holdc);
    g.setColor(foreground);
    UISettings.setupAntialiasing(g);
    paintText(g, lh, lr);
    paintAccText(g, lh, lr);
    paintArrowIcon(g, lh, lr, foreground);

    // Restore original graphics font and color
    g.setColor(holdc);
    g.setFont(holdf);
  }

  protected void paintIcon(Graphics g, MenuItemLayoutHelper lh,
                         MenuItemLayoutHelper.LayoutResult lr, Color holdc) {
      if (lh.getIcon() != null) {
          Icon icon;
          ButtonModel model = lh.getMenuItem().getModel();
          if (!model.isEnabled()) {
              icon = lh.getMenuItem().getDisabledIcon();
          } else if (model.isPressed() && model.isArmed()) {
              icon = lh.getMenuItem().getPressedIcon();
              if (icon == null) {
                  // Use default icon
                  icon = lh.getMenuItem().getIcon();
              }
          } else {
              icon = lh.getMenuItem().getIcon();
          }

          if (icon != null) {
              icon.paintIcon(lh.getMenuItem(), g, lr.getIconRect().x,
                      lr.getIconRect().y);
              g.setColor(holdc);
          }
      }
  }

  protected void paintCheckIcon(Graphics g, MenuItemLayoutHelper lh,
                              MenuItemLayoutHelper.LayoutResult lr,
                              Color holdc, Color foreground) {
      if (lh.getCheckIcon() != null) {
          ButtonModel model = lh.getMenuItem().getModel();
          if (model.isArmed() || (lh.getMenuItem() instanceof JMenu
                  && model.isSelected())) {
              g.setColor(foreground);
          } else {
              g.setColor(holdc);
          }
          if (lh.useCheckAndArrow()) {
              lh.getCheckIcon().paintIcon(lh.getMenuItem(), g,
                      lr.getCheckRect().x, lr.getCheckRect().y);
          }
          g.setColor(holdc);
      }
  }

  protected void paintAccText(Graphics g, MenuItemLayoutHelper lh,
                              MenuItemLayoutHelper.LayoutResult lr) {
    if (!lh.getAccText().equals("")) {
      ButtonModel model = lh.getMenuItem().getModel();
      g.setFont(lh.getAccFontMetrics().getFont());
      if (!model.isEnabled()) {
        // *** paint the accText disabled
        if (disabledForeground != null) {
          g.setColor(disabledForeground);
          SwingUtilities2.drawString(lh.getMenuItem(), g,
              lh.getAccText(), lr.getAccRect().x,
              lr.getAccRect().y + lh.getAccFontMetrics().getAscent());
        } else {
          g.setColor(lh.getMenuItem().getBackground().brighter());
          SwingUtilities2.drawString(lh.getMenuItem(), g,
              lh.getAccText(), lr.getAccRect().x,
              lr.getAccRect().y + lh.getAccFontMetrics().getAscent());
          g.setColor(lh.getMenuItem().getBackground().darker());
          SwingUtilities2.drawString(lh.getMenuItem(), g,
              lh.getAccText(), lr.getAccRect().x - 1,
              lr.getAccRect().y + lh.getFontMetrics().getAscent() - 1);
        }
      } else {
        // *** paint the accText normally
        if (model.isArmed()
            || (lh.getMenuItem() instanceof JMenu
            && model.isSelected())) {
          g.setColor(acceleratorSelectionForeground);
        } else {
          g.setColor(acceleratorForeground);
        }
        SwingUtilities2.drawString(lh.getMenuItem(), g, lh.getAccText(),
                                   lr.getAccRect().x, lr.getAccRect().y +
                                                      lh.getAccFontMetrics().getAscent());
      }
    }
  }

  protected void paintText(Graphics g, MenuItemLayoutHelper lh,
                         MenuItemLayoutHelper.LayoutResult lr) {
      if (!lh.getText().equals("")) {
          if (lh.getHtmlView() != null) {
              // Text is HTML
              lh.getHtmlView().paint(g, lr.getTextRect());
          } else {
              // Text isn't HTML
              UISettings.setupAntialiasing(g);
              paintText(g, lh.getMenuItem(), lr.getTextRect(), lh.getText());
          }
      }
  }

  protected void paintArrowIcon(Graphics g, MenuItemLayoutHelper lh,
                              MenuItemLayoutHelper.LayoutResult lr,
                              Color foreground) {
      if (lh.getArrowIcon() != null) {
          ButtonModel model = lh.getMenuItem().getModel();
          if (model.isArmed() || (lh.getMenuItem() instanceof JMenu
                              && model.isSelected())) {
              g.setColor(foreground);
          }
          if (lh.useCheckAndArrow()) {
              lh.getArrowIcon().paintIcon(lh.getMenuItem(), g,
                      lr.getArrowRect().x, lr.getArrowRect().y);
          }
      }
  }
}
