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

import com.intellij.ui.JBColor;
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

import static com.intellij.util.ui.JBUI.CurrentTheme.TabbedPane.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTabbedPaneUI extends BasicTabbedPaneUI {
  private enum TabStyle {
    underline, fill
  }

  private TabStyle tabStyle;
  private PropertyChangeListener panePropertyListener;
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
    contentBorderInsets = tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT ? JBUI.insetsTop(1) : JBUI.emptyInsets();
  }

  @Override
  protected void installListeners() {
    super.installListeners();

    panePropertyListener = evt -> {
      String propName = evt.getPropertyName();
      if ("JTabbedPane.hasFullBorder".equals(propName) || "tabLayoutPolicy".equals(propName)) {
        boolean fullBorder = tabPane.getClientProperty("JTabbedPane.hasFullBorder") == Boolean.TRUE;
        contentBorderInsets = (tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT) ?
                              fullBorder ? JBUI.insets(1) : JBUI.insetsTop(1) :
                              fullBorder ? JBUI.insets(0, 1, 1, 1) : JBUI.emptyInsets();
        tabPane.revalidate();
      } else if ("enabled".equals(propName)) {
        for (int ti = 0; ti < tabPane.getTabCount(); ti++) {
          Component tc = tabPane.getTabComponentAt(ti);
          if (tc != null) {
            tc.setEnabled(evt.getNewValue() == Boolean.TRUE);
          }
        }
      }
      tabPane.repaint();
    };

    tabPane.addPropertyChangeListener("JTabbedPane.hasFullBorder", panePropertyListener);
    tabPane.addPropertyChangeListener("tabLayoutPolicy", panePropertyListener);
    tabPane.addPropertyChangeListener("enabled", panePropertyListener);

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
    if (panePropertyListener != null) {
      tabPane.removePropertyChangeListener("JTabbedPane.hasFullBorder", panePropertyListener);
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
  protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
    if (tabPane.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT) {
      Rectangle bounds = g.getClipBounds();
      g.setColor(JBColor.namedColor("TabbedPane.contentAreaColor", 0xbfbfbf));

      if (tabPlacement == LEFT || tabPlacement == RIGHT) {
        g.fillRect(bounds.x + bounds.width - OFFSET.get(), bounds.y, OFFSET.get(), bounds.y + bounds.height);
      } else {
        g.fillRect(bounds.x, bounds.y + bounds.height - OFFSET.get(), bounds.x + bounds.width, OFFSET.get());
      }
    }
    super.paintTabArea(g, tabPlacement, selectedIndex);
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
        g.setColor(tabPane.isEnabled() && tabIndex == hoverTab ? HOVER_COLOR : tabPane.getBackground());
        break;
    }

    if (tabPane.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT) {
      if (tabPlacement == LEFT || tabPlacement == RIGHT) {
        w -= OFFSET.get();
      } else {
        h -= OFFSET.get();
      }
    }

    g.fillRect(x, y, w, h);
  }

  @Override
  protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex,
                           String title, Rectangle textRect, boolean isSelected) {

    View v = getTextViewForTab(tabIndex);
    if (v != null || tabPane.isEnabled() && tabPane.isEnabledAt(tabIndex)) {
      super.paintText(g, tabPlacement, font, metrics, tabIndex, title, textRect, isSelected);
    }
    else { // tab disabled
      int mnemIndex = tabPane.getDisplayedMnemonicIndexAt(tabIndex);

      g.setFont(font);
      g.setColor(DISABLED_TEXT_COLOR);
      SwingUtilities2.drawStringUnderlineCharAt(tabPane, g, title, mnemIndex, textRect.x, textRect.y + metrics.getAscent());
    }
  }

  @Override
  protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
    if (isSelected && tabStyle == TabStyle.underline) {
      g.setColor(tabPane.isEnabled() ?  ENABLED_SELECTED_COLOR : DISABLED_SELECTED_COLOR);

      int offset;
      boolean wrap = tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT;
      switch(tabPlacement) {
        case LEFT:
          offset = SELECTION_HEIGHT.get() - (wrap ? OFFSET.get() : 0);
          g.fillRect(x + w - offset, y, SELECTION_HEIGHT.get(), h);
          break;
        case RIGHT:
          offset = wrap ? OFFSET.get() : 0;
          g.fillRect(x - offset, y, SELECTION_HEIGHT.get(), h);
          break;
        case BOTTOM:
          offset = wrap ? OFFSET.get() : 0;
          g.fillRect(x, y - offset, w, SELECTION_HEIGHT.get());
          break;
        case TOP:
        default:
          offset = SELECTION_HEIGHT.get() - (wrap ? OFFSET.get() : 0);
          g.fillRect(x, y + h - offset, w, SELECTION_HEIGHT.get());
          break;
      }
    }
  }

  @Override
  protected int getTabLabelShiftY(int tabPlacement, int tabIndex, boolean isSelected) {
    int delta = SELECTION_HEIGHT.get();
    if (tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT) {
      delta -= OFFSET.get();
    }

    switch(tabPlacement) {
      case RIGHT:
      case LEFT:
        return 0;

      case BOTTOM:
        return delta/2;

      case TOP:
      default:
        return -delta/2;
    }
  }

  @Override
  protected int getTabLabelShiftX(int tabPlacement, int tabIndex, boolean isSelected) {
    int delta = SELECTION_HEIGHT.get();
    if (tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT) {
      delta -= OFFSET.get();
    }

    switch(tabPlacement) {
      case TOP:
      case BOTTOM:
        return 0;

      case LEFT:
        return -delta/2;

      case RIGHT:
      default:
        return delta/2;
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

    int minHeight = TAB_HEIGHT.get() - (tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT ? OFFSET.get() : 0);
    return Math.max(height, minHeight);
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
