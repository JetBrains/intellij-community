/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.navigationToolbar.ui;

import com.intellij.ide.navigationToolbar.NavBarItem;
import com.intellij.ide.navigationToolbar.NavBarPanel;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColorUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

/**
 * @author Konstantin Bulenkov
 */
public abstract class AbstractNavBarUI implements NavBarUI {
  //private static Image SEPARATOR_ACTIVE = IconUtil.toImage(IconLoader.getIcon("/general/navbarSeparatorActive.png"));
  static Image SEPARATOR_PASSIVE = IconUtil.toImage(IconLoader.getIcon("/general/navbarSeparatorPassive.png"));
  static Image SEPARATOR_GRADIENT = IconUtil.toImage(IconLoader.getIcon("/general/navbarSeparatorGradient.png"));

  @Override
  public Insets getElementIpad(boolean isPopupElement) {
    return isPopupElement ? new Insets(1, 2, 1, 2) : JBInsets.NONE;
  }

  @Override
  public JBInsets getElementPadding() {
    return new JBInsets(3, 3, 3, 3);
  }

  @Override
  public Font getElementFont(NavBarItem navBarItem) {
    return navBarItem.getFont();
  }

  @Override
  public Color getBackground(boolean selected, boolean focused) {
    return selected && focused ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground();
  }

  @Nullable
  @Override
  public Color getForeground(boolean selected, boolean focused, boolean inactive) {
    return selected && focused ? UIUtil.getListSelectionForeground()
                               : inactive ? UIUtil.getInactiveTextColor() : null;
  }

  @Override
  public short getSelectionAlpha() {
    if ((UIUtil.isUnderAlloyLookAndFeel() && !UIUtil.isUnderAlloyIDEALookAndFeel())
        || UIUtil.isUnderMetalLookAndFeel() 
        || UIUtil.isUnderMetalLookAndFeel()) {
      return 255;
    }
    return 150;
  }

  @Override
  public boolean isDrawMacShadow(boolean selected, boolean focused) {
    return false;
  }

  @Override
  public void doPaintNavBarItem(Graphics2D g, NavBarItem item, NavBarPanel navbar) {
    Icon icon = item.getIcon();
    final Color bg = item.isSelected() && item.isFocused()
                      ? UIUtil.getListSelectionBackground()
                      : (UIUtil.isUnderGTKLookAndFeel() ? Color.WHITE : UIUtil.getListBackground());
    final Color c = UIUtil.getListSelectionBackground();
    final Color selBg = new Color(c.getRed(), c.getGreen(), c.getBlue(), getSelectionAlpha());
    int w = item.getWidth();
    int h = item.getHeight();
    if (navbar.isInFloatingMode() || (item.isSelected() && navbar.hasFocus())) {
      g.setPaint(item.isSelected() && item.isFocused() ? selBg : bg);
      g.fillRect(0, 0, w - (item.isLastElement() ? 0 : getDecorationOffset()), h);
    }
    final int offset = item.isFirstElement() ? getFirstElementLeftOffset() : 0;
    final int iconOffset = getElementPadding().left + offset;
    icon.paintIcon(item, g, iconOffset, (h - icon.getIconHeight()) / 2);
    final int textOffset = icon.getIconWidth() + getElementPadding().width() + offset;
    int x = item.doPaintText(g, textOffset);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    
    g.translate(x, 0);
    Path2D.Double path;
    int off = getDecorationOffset();
    if (item.isFocused()) {
      if (item.isSelected() && !item.isLastElement()) {
        path = new Path2D.Double();
        g.translate(2, 0);
        path.moveTo(0, 0);
        path.lineTo(off, h / 2);              // |\
        path.lineTo(0, h);                    // |/
        path.lineTo(0, 0);
        g.setColor(selBg);
        g.fill(path);
        g.translate(-2, 0);
      }

      if (navbar.isInFloatingMode() || item.isNextSelected()) {
        if (! item.isLastElement()) {
          path = new Path2D.Double();
          path.moveTo(0, 0);
          path.lineTo(off, h / 2);                // ___
          path.lineTo(0, h);                      // \ |
          path.lineTo(off + 2, h);                // /_|
          path.lineTo(off + 2, 0);
          path.lineTo(0, 0);
          g.setColor(item.isNextSelected() ? selBg : UIUtil.getListBackground());
          g.fill(path);
        }
      }
    }
    if (! item.isLastElement() && ((!item.isSelected() && !item.isNextSelected()) || !navbar.hasFocus())) {
      Image img = SEPARATOR_PASSIVE;
      final UISettings settings = UISettings.getInstance();
      if (settings.SHOW_NAVIGATION_BAR) {
        img = SEPARATOR_GRADIENT;
      }
      g.drawImage(img, null, null);
    }    
  }

  private int getDecorationOffset() {
     return 11;
   }

   private int getFirstElementLeftOffset() {
     return 6;
   }

  @Override
  public Dimension getOffsets(NavBarItem item) {
    final Dimension size = new Dimension();
    if (! item.isPopupElement()) {
      size.width += getDecorationOffset() + getElementPadding().width() + (item.isFirstElement() ? getFirstElementLeftOffset() : 0);
      size.height += getElementPadding().height();
    }
    return size;
  }

  @Override
  public void doPaintWrapperPanelChildren(Graphics2D g, Rectangle bounds, boolean mainToolbarVisible) {
  }

  @Override
  public Insets getWrapperPanelInsets(Insets insets) {
    return JBInsets.NONE;
  }

  @Override
  public void doPaintNavBarPanel(Graphics2D g, Rectangle r, boolean mainToolbarVisible, boolean undocked) {
    final Color startColor = UIUtil.getControlColor();
    final Color endColor = ColorUtil.shift(startColor, 7.0d / 8.0d);
    g.setPaint(new GradientPaint(0, 0, startColor, 0, r.height, endColor));
    g.fillRect(0, 0, r.width, r.height);

    if (!undocked) {
      g.setColor(new Color(255, 255, 255, 220));
      g.drawLine(0, 1, r.width, 1);
    }

    g.setColor(UIUtil.getBorderColor());
    if (!undocked) g.drawLine(0, 0, r.width, 0);
    g.drawLine(0, r.height-1, r.width, r.height-1);

    if (!mainToolbarVisible) {
      UIUtil.drawDottedLine(g, r.width - 1, 0, r.width - 1, r.height, null, Color.GRAY);
    }
  }
}
