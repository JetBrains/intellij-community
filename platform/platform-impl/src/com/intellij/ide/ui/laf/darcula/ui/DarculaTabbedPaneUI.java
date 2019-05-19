// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import com.intellij.util.ui.UIUtilities;

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
  private MouseListener paneMouseListener;
  private MouseMotionListener paneMouseMotionListener;

  private int hoverTab = -1;

  private static final JBValue OFFSET = new JBValue.Float(1);
  private static final JBValue FONT_SIZE_OFFSET = new JBValue.UIInteger("TabbedPane.fontSizeOffset", -1);

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaTabbedPaneUI();
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();

    modifyFontSize();

    Object rStyle = UIManager.get("TabbedPane.tabFillStyle");
    tabStyle = rStyle != null ? TabStyle.valueOf(rStyle.toString()) : TabStyle.underline;
    contentBorderInsets = tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT ? JBUI.insetsTop(1) : JBUI.emptyInsets();
  }

  private void modifyFontSize() {
    if (SystemInfo.isMac || SystemInfo.isLinux) {
      Font font = UIManager.getFont("TabbedPane.font");
      tabPane.setFont(tabPane.getFont().deriveFont((float)font.getSize() + FONT_SIZE_OFFSET.get()));
    }
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
        tabPane.repaint();
      }
      else if ("enabled".equals(propName)) {
        for (int ti = 0; ti < tabPane.getTabCount(); ti++) {
          Component tc = tabPane.getTabComponentAt(ti);
          if (tc != null) {
            tc.setEnabled(evt.getNewValue() == Boolean.TRUE);
          }
        }
        tabPane.repaint();
      }
    };

    tabPane.addPropertyChangeListener(panePropertyListener);

    paneMouseListener = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        hoverTab = tabForCoordinate(tabPane, e.getX(), e.getY());
        tabPane.repaint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        hoverTab = -1;
        tabPane.repaint();
      }
    };

    tabPane.addMouseListener(paneMouseListener);

    paneMouseMotionListener = new MouseMotionAdapter() {
      @Override
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
      tabPane.removePropertyChangeListener(panePropertyListener);
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
      }
      else {
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
        }
        else {
          g.setColor(isSelected ? DISABLED_SELECTED_COLOR : tabPane.getBackground());
        }
        break;

      case underline:
      default:
        Color c = tabPane.getBackground();
        if (tabPane.isEnabled()) {
          if (tabPane.hasFocus() && isSelected) {
            c = FOCUS_COLOR;
          }
          else if (tabIndex == hoverTab) {
            c = HOVER_COLOR;
          }
        }

        g.setColor(c);
        break;
    }

    if (tabPane.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT) {
      if (tabPlacement == LEFT || tabPlacement == RIGHT) {
        w -= OFFSET.get();
      }
      else {
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
      UIUtilities.drawStringUnderlineCharAt(tabPane, g, title, mnemIndex, textRect.x, textRect.y + metrics.getAscent());
    }
  }

  @Override
  protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
    if (isSelected && tabStyle == TabStyle.underline) {
      g.setColor(tabPane.isEnabled() ? ENABLED_SELECTED_COLOR : DISABLED_SELECTED_COLOR);

      int offset;
      boolean wrap = tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT;
      switch (tabPlacement) {
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

    switch (tabPlacement) {
      case RIGHT:
      case LEFT:
        return 0;

      case BOTTOM:
        return delta / 2;

      case TOP:
      default:
        return -delta / 2;
    }
  }

  @Override
  protected int getTabLabelShiftX(int tabPlacement, int tabIndex, boolean isSelected) {
    int delta = SELECTION_HEIGHT.get();
    if (tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT) {
      delta -= OFFSET.get();
    }

    switch (tabPlacement) {
      case TOP:
      case BOTTOM:
        return 0;

      case LEFT:
        return -delta / 2;

      case RIGHT:
      default:
        return delta / 2;
    }
  }

  @Override
  protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
    return super.calculateTabWidth(tabPlacement, tabIndex, metrics) - 3; //remove magic constant '3' added by parent
  }

  @Override
  protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
    int height = super.calculateTabHeight(tabPlacement, tabIndex, fontHeight) - 2; //remove magic constant '2' added by parent
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

  @Override
  protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect,
                                     boolean isSelected) {}
}
