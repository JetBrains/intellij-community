/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.tabs.impl;

import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBEditorTabsPainter {
  protected Color myDefaultTabColor;
  protected final JBEditorTabs myTabs;

  protected static final Color BORDER_COLOR = JBColor.namedColor("EditorTabs.borderColor", UIUtil.CONTRAST_BORDER_COLOR);
  protected static final Color UNDERLINE_COLOR = JBColor.namedColor("EditorTabs.underlineColor", 0x439EB8);
  protected static final Color DEFAULT_TAB_COLOR = JBUI.CurrentTheme.EditorTabs.backgroundColor();
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

  public abstract void paintSelectionAndBorder(Graphics2D g2d,
                                      Rectangle rect,
                                      JBTabsImpl.ShapeInfo selectedShape,
                                      Insets insets,
                                      Color tabColor);

  protected void fillRect(Graphics2D g2d,
                          Rectangle rect,
                          Color tabColor) {
    if(tabColor != null) {
      g2d.setColor(tabColor);
      g2d.fillRect(rect.x, rect.y, rect.width, rect.height);
    }
  }

  protected boolean hasFocus(Component component) {
    Component focusOwner = findFocusOwner(component);
    return focusOwner != null;
  }

  private static Component findFocusOwner(Component c) {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

    // verify focusOwner is a descendant of c
    for (Component temp = focusOwner; temp != null; temp = (temp instanceof Window) ? null : temp.getParent())
    {
      if (temp == c) {
        return focusOwner;
      }
    }

    return null;
  }

  public abstract Color getBackgroundColor();

  public Color getEmptySpaceColor() {
    return UIUtil.isUnderAquaLookAndFeel() ? Gray.xC8 : UIUtil.getPanelBackground();
  }

  public void setDefaultTabColor(Color color) {
    myDefaultTabColor = color;
  }
}
