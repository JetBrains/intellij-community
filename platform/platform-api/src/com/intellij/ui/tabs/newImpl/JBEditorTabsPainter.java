// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl;

import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @deprecated left for API compatibility
 * @author Konstantin Bulenkov
 */
@Deprecated
public abstract class JBEditorTabsPainter {
  protected Color myDefaultTabColor;
  protected final JBEditorTabs myTabs;

  protected static final Color BORDER_COLOR = JBColor.namedColor("EditorTabs.borderColor", UIUtil.CONTRAST_BORDER_COLOR);
  protected static final Color UNDERLINE_COLOR = JBColor.namedColor("EditorTabs.underlineColor", 0x439EB8);
  protected static final Color DEFAULT_TAB_COLOR = JBColor.namedColor("EditorTabs.selectedBackground", new JBColor(0xFFFFFF, 0x515658));
  protected static final Color INACTIVE_MASK_COLOR = JBColor.namedColor("EditorTabs.inactiveMaskColor",
                                                                        new JBColor(ColorUtil.withAlpha(Gray.x26, .2),
                                                                                    ColorUtil.withAlpha(Gray.x26, .5)));

  public JBEditorTabsPainter(JBEditorTabs tabs) {
    myTabs = tabs;
  }

  public abstract void doPaintInactive(Graphics2D g2d,
                       Rectangle effectiveBounds,
                       int x,
                       int y,
                       int w,
                       int h,
                       Color tabColor,
                       int row,
                       int column,
                       boolean vertical);

  public abstract void doPaintBackground(Graphics2D g, Rectangle clip, boolean vertical, Rectangle rectangle);
  public abstract void fillSelectionAndBorder(Graphics2D g, JBTabsImpl.ShapeInfo selectedShape, Color tabColor, int x, int y, int height);

  public void paintSelectionAndBorder(Graphics2D g2d,
                                      Rectangle rect,
                                      JBTabsImpl.ShapeInfo selectedShape,
                                      Insets insets,
                                      Color tabColor) {
  }


  public abstract Color getBackgroundColor();

  public Color getEmptySpaceColor() {
    return UIUtil.isUnderAquaLookAndFeel() ? Gray.xC8 : UIUtil.getPanelBackground();
  }

  public void setDefaultTabColor(Color color) {
    myDefaultTabColor = color;
  }
}
