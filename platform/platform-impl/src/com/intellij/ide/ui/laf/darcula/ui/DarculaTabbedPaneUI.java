/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;

import static com.intellij.util.ui.JBUI.CurrentTheme.Focus.TabbedPane.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTabbedPaneUI extends BasicTabbedPaneUI {
  private enum TabStyle {
    underline, fill
  }

  private TabStyle tabStyle;
  private PropertyChangeListener borderTypePropertyListener;
  private MouseListener          paneMouseListener;
  private MouseMotionListener    paneMouseMotionListener;

  private int hoverTab = -1;

  public static final JBValue OFFSET = new JBValue.Float(1);

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaTabbedPaneUI();
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();

    Object rStyle = UIManager.get("TabbedPane.tabFillStyle");
    tabStyle = rStyle != null ? TabStyle.valueOf(rStyle.toString()) : TabStyle.underline;
    contentBorderInsets = JBUI.insetsTop(1);
  }

  @Override
  protected void installListeners() {
    super.installListeners();

    borderTypePropertyListener = evt -> {
      contentBorderInsets = evt.getNewValue() == Boolean.TRUE ? JBUI.insets(1) : JBUI.insetsTop(1);
      tabPane.revalidate();
      tabPane.repaint();
    };

    tabPane.addPropertyChangeListener("JTabbedPane.hasFullBorder", borderTypePropertyListener);

    paneMouseListener = new MouseAdapter() {
      public void mouseEntered(MouseEvent e) {
        hoverTab = tabForCoordinate(tabPane, e.getX(), e.getY());
        tabPane.repaint();
      }

      public void mouseExited(MouseEvent e) {
        hoverTab = -1;
        tabPane.repaint();
      }
    };

    tabPane.addMouseListener(paneMouseListener);

    paneMouseMotionListener = new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {
        hoverTab = tabForCoordinate(tabPane, e.getX(), e.getY());
        tabPane.repaint();
      }
    };
    tabPane.addMouseMotionListener(paneMouseMotionListener);
  }

  @Override
  protected void uninstallListeners() {
    super.uninstallListeners();
    if (borderTypePropertyListener != null) {
      tabPane.removePropertyChangeListener("JTabbedPane.hasFullBorder", borderTypePropertyListener);
    }

    if (paneMouseListener != null) {
      tabPane.removeMouseListener(paneMouseListener);
    }

    if (paneMouseMotionListener != null) {
      tabPane.removeMouseMotionListener(paneMouseMotionListener);
    }
  }

  @Override
  protected Insets getContentBorderInsets(int tabPlacement) {
    Insets i = JBInsets.create(contentBorderInsets);
    rotateInsets(contentBorderInsets, i, tabPlacement);
    return i;
  }

  @Override
  protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
    switch (tabStyle) {
      case fill:
        if (tabPane.isEnabled()) {
          g.setColor(isSelected ? ENABLED_SELECTED_COLOR : tabIndex == hoverTab ? HOVER_COLOR : tabPane.getBackground());
        } else {
          g.setColor(isSelected ? DISABLED_SELECTED_COLOR : tabPane.getBackground());
        }
        break;

      case underline:
      default:
        if (tabPane.isEnabled()) {
          g.setColor(tabIndex == hoverTab ? HOVER_COLOR : tabPane.getBackground());
        }
        break;
    }
    g.fillRect(x, y, w, h);
  }

  @Override
  protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
    if (isSelected && tabStyle == TabStyle.underline) {
      g.setColor(tabPane.isEnabled() ?  ENABLED_SELECTED_COLOR : DISABLED_SELECTED_COLOR);

      switch(tabPlacement) {
        case LEFT:
          g.fillRect(x + w - OFFSET.get(), y, SELECTION_HEIGHT.get(), h);
          break;
        case RIGHT:
          g.fillRect(x - OFFSET.get(), y, SELECTION_HEIGHT.get(), h);
          break;
        case BOTTOM:
          g.fillRect(x, y - OFFSET.get(), w, SELECTION_HEIGHT.get());
          break;
        case TOP:
        default:
          g.fillRect(x, y + h - OFFSET.get(), w, SELECTION_HEIGHT.get());
      }
    }
  }

  @Override
  protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
    Insets tabInsets = getTabInsets(tabPlacement, tabIndex);
    int width = tabInsets.left + tabInsets.right;
    Component tabComponent = tabPane.getTabComponentAt(tabIndex);
    if (tabComponent != null) {
      width += tabComponent.getPreferredSize().width;
    } else {
      Icon icon = getIconForTab(tabIndex);
      if (icon != null) {
        width += icon.getIconWidth() + textIconGap;
      }
      View v = getTextViewForTab(tabIndex);
      if (v != null) {
        // html
        width += (int) v.getPreferredSpan(View.X_AXIS);
      } else {
        // plain text
        String title = tabPane.getTitleAt(tabIndex);
        width += SwingUtilities2.stringWidth(tabPane, metrics, title);
      }
    }
    return width;
  }

  @Override
  protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
    int height = 0;
    Component c = tabPane.getTabComponentAt(tabIndex);
    if (c != null) {
      height = c.getPreferredSize().height;
    } else {
      View v = getTextViewForTab(tabIndex);
      if (v != null) {
        // html
        height += (int) v.getPreferredSpan(View.Y_AXIS);
      } else {
        // plain text
        height += fontHeight;
      }
      Icon icon = getIconForTab(tabIndex);

      if (icon != null) {
        height = Math.max(height, icon.getIconHeight());
      }
    }
    Insets tabInsets = getTabInsets(tabPlacement, tabIndex);
    height += tabInsets.top + tabInsets.bottom;
    return Math.max(height, TAB_HEIGHT.get() - OFFSET.get());
  }

  @Override
  protected void paintContentBorderTopEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) {}

  @Override
  protected void paintContentBorderLeftEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) {}

  @Override
  protected void paintContentBorderRightEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) {}

  @Override
  protected void paintContentBorderBottomEdge(Graphics g, int tabPlacement, int selectedIndex, int x, int y, int w, int h) {}
}
