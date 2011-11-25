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
import com.intellij.ide.navigationToolbar.NavBarRootPaneExtension;
import com.intellij.ide.ui.UISettings;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.SameColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

/**
 * @author Konstantin Bulenkov
 */
public abstract class AbstractNavBarUI implements NavBarUI {
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
    final boolean floating = navbar.isInFloatingMode();
    boolean toolbar = UISettings.getInstance().SHOW_MAIN_TOOLBAR;

    Icon icon = item.getIcon();
    int w = item.getWidth();
    int h = item.getHeight();

    final Paint bg = floating ? Color.WHITE : new GradientPaint(0, 0, new Color(255, 255, 255, 30), 0, h, new Color(255, 255, 255, 10));
    final Color selection = UIUtil.getListSelectionBackground();
    
    final boolean selected = item.isSelected() && item.isFocused();
    
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    Path2D.Double shape = new Path2D.Double();
    shape.moveTo(0, 0);
    shape.lineTo(w - getDecorationOffset(), 0);
    shape.lineTo(w, h / 2);
    shape.lineTo(w - getDecorationOffset(), h);
    shape.lineTo(0, h);
    shape.closePath();
    
    Path2D.Double endShape = new Path2D.Double();
    endShape.moveTo(w - getDecorationOffset(), 0);
    endShape.lineTo(w, 0);
    endShape.lineTo(w, h);
    endShape.lineTo(w - getDecorationOffset(), h);
    endShape.lineTo(w, h / 2);
    endShape.closePath();
    
    if (bg != null && toolbar) {
      g2.setPaint(bg);
      g2.fill(shape);
      if (!item.isLastElement() || floating) {
        g2.fill(endShape);
      }
    }

    if (selected) {
      Path2D.Double focusShape = new Path2D.Double();
      if (toolbar || floating) {
        focusShape.moveTo(w-getDecorationOffset(), 0);
      } else {
        focusShape.moveTo(0, 0);
        focusShape.lineTo(w - getDecorationOffset(), 0);
      }
      focusShape.lineTo(w - 1, h / 2);
      focusShape.lineTo(w - getDecorationOffset(), h - 1);
      if (!toolbar && !floating) {
        focusShape.lineTo(0, h - 1);
        
      }

      g2.setColor(selection);
      if (floating && item.isLastElement()) {
        g2.fillRect(0, 0, w, h);
      } else {
        g2.fill(shape);
        
        g2.setColor(new Color(0, 0, 0, 70));
        g2.draw(focusShape);
      }
    }

    if (item.isNextSelected() && navbar.hasFocus()) {
      g2.setColor(selection);
      g2.fill(endShape);
      
      Path2D.Double endFocusShape = new Path2D.Double();
      if (toolbar || floating) {
        endFocusShape.moveTo(w - getDecorationOffset(), 0);
      } else {
        endFocusShape.moveTo(w, 0);
        endFocusShape.lineTo(w - getDecorationOffset(), 0);
      }
      
      endFocusShape.lineTo(w - 1, h / 2);
      endFocusShape.lineTo(w - getDecorationOffset(), h - 1);
      
      if (!toolbar && !floating) {
        endFocusShape.lineTo(w, h - 1);
      }

      g2.setColor(new Color(0, 0, 0, 70));
      g2.draw(endFocusShape);
    }

    final int offset = item.isFirstElement() ? getFirstElementLeftOffset() : 0;
    final int iconOffset = getElementPadding().left + offset;
    icon.paintIcon(item, g2, iconOffset, (h - icon.getIconHeight()) / 2);
    final int textOffset = icon.getIconWidth() + getElementPadding().width() + offset;
    item.doPaintText(g2, textOffset);
    
    g2.translate(w - getDecorationOffset(), 0);
    int off = getDecorationOffset() - 1;

    if (!floating || !item.isLastElement()) {
      if (toolbar || floating) {
        if (!selected && (!navbar.hasFocus() | !item.isNextSelected())) {
          drawArrow(g2, new Color(0, 0, 0, 70), new SameColor(205), off, h, !selected && !floating, false);
        }
      } else {
        if (!selected && (!navbar.hasFocus() | !item.isNextSelected())) {
          drawArrow(g2, new Color(0, 0, 0, 150), new Color(255, 255, 255, 200), off, h, !selected && !floating, true);
        }
      }
    }
    
    g2.dispose();
  }
  
  private static void drawArrow(Graphics2D g2d, Color c, Color light, int decorationOffset, int h, boolean highlight, boolean gradient) {
    int off = decorationOffset - 1;
    
    g2d.setColor(c);
    if (gradient) {
      g2d.setPaint(new GradientPaint(0, 0, new Color(c.getRed(), c.getGreen(), c.getBlue(), 10), 0, h / 2, c));
    }
    g2d.drawLine(0, 0, off, h / 2);

    if (gradient) {
      g2d.setPaint(new GradientPaint(0, h / 2, c, 0, h, new Color(c.getRed(), c.getGreen(), c.getBlue(), 10)));
    }
    g2d.drawLine(off, h / 2, 0, h);

    if (highlight) {
      g2d.translate(-1, 0);
      g2d.setColor(light);
      
      if (gradient) {
        g2d.setPaint(new GradientPaint(0, 0, new Color(light.getRed(), light.getGreen(), light.getBlue(), 10), 0, h / 2, light));
      }
      g2d.drawLine(0, 0, off, h / 2);


      if (gradient) {
        g2d.setPaint(new GradientPaint(0, h / 2, light, 0, h, new Color(light.getRed(), light.getGreen(), light.getBlue(), 10)));
      }
      g2d.drawLine(off, h / 2, 0, h);
    }
  }

  private int getDecorationOffset() {
     return 8;
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
  public Insets getWrapperPanelInsets(Insets insets) {
    return new Insets(insets.top + (shouldPaintWrapperPanel() ? 1 : 0), insets.left, insets.bottom, insets.right);
  }
  
  private static boolean shouldPaintWrapperPanel() {
    return !UISettings.getInstance().SHOW_MAIN_TOOLBAR && NavBarRootPaneExtension.runToolbarExists(); 
  }

  protected Color getBackgroundColor() {
    return UIUtil.getSlightlyDarkerColor(UIUtil.getPanelBackground());
  }
  
  @Override
  public void doPaintNavBarPanel(Graphics2D g, Rectangle r, boolean mainToolbarVisible, boolean undocked) {
    g.setColor(getBackgroundColor());
    if (mainToolbarVisible) {
      g.fillRect(0, 0, r.width, r.height);
    }
  }
}
