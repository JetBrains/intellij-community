// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.GeneralPath;

import static javax.swing.SwingConstants.*;

public final class TabsUtil {
  public static final int NEW_TAB_VERTICAL_PADDING = JBUIScale.scale(2);
  @ApiStatus.Internal
  public static final int UNSCALED_DROP_TOLERANCE = 15;

  private TabsUtil() {
  }

  public static int getTabsHeight() {
    return getTabsHeight(NEW_TAB_VERTICAL_PADDING);
  }

  public static int getTabsHeight(int verticalPadding) {
    @SuppressWarnings("HardCodedStringLiteral") JLabel xxx = new JLabel("XXX");
    xxx.setFont(getLabelFont());
    return xxx.getPreferredSize().height + (verticalPadding * 2);
  }

  public static Font getLabelFont() {
    UISettings uiSettings = UISettings.getInstance();
    Font font = JBUI.CurrentTheme.ToolWindow.headerFont();
    if (uiSettings.getOverrideLafFonts()) {
      return font.deriveFont(uiSettings.getFontSize2D() + JBUI.CurrentTheme.ToolWindow.overrideHeaderFontSizeOffset());
    }

    return font;
  }

  @MagicConstant(intValues = {CENTER, TOP, LEFT, BOTTOM, RIGHT, -1})
  public static int getDropSideFor(Point point, JComponent component) {
    double r = Math.max(.05, Math.min(.45, Registry.doubleValue("ide.tabbedPane.dragToSplitRatio")));

    Rectangle rect = new Rectangle(new Point(0, 0), component.getSize());
    double width = rect.getWidth();
    double height = rect.getHeight();
    GeneralPath topShape = new GeneralPath();
    topShape.moveTo(0, 0);
    topShape.lineTo(width, 0);
    topShape.lineTo(width * (1 - r), height * r);
    topShape.lineTo(width  * r, height  * r);
    topShape.closePath();

    GeneralPath leftShape = new GeneralPath();
    leftShape.moveTo(0, 0);
    leftShape.lineTo(width  * r, height  * r);
    leftShape.lineTo(width  * r, height * (1 - r));
    leftShape.lineTo(0, height);
    leftShape.closePath();

    GeneralPath bottomShape = new GeneralPath();
    bottomShape.moveTo(0, height);
    bottomShape.lineTo(width  * r, height * (1 - r));
    bottomShape.lineTo(width * (1 - r), height * (1 - r));
    bottomShape.lineTo(width, height);
    bottomShape.closePath();

    GeneralPath rightShape = new GeneralPath();
    rightShape.moveTo(width, 0);
    rightShape.lineTo(width * (1 - r), height  * r);
    rightShape.lineTo(width * (1 - r), height * (1 - r));
    rightShape.lineTo(width, height);
    rightShape.closePath();

    if (rightShape.contains(point)) return RIGHT;
    if (leftShape.contains(point)) return LEFT;
    if (bottomShape.contains(point)) return BOTTOM;
    if (topShape.contains(point)) return TOP;
    return rect.contains(point) ? CENTER : -1;
  }

  public static void updateBoundsWithDropSide(@NotNull Rectangle bounds,
                                              @MagicConstant(intValues = {CENTER, TOP, LEFT, BOTTOM, RIGHT, -1}) int dropSide) {
    switch (dropSide) {
      case TOP -> bounds.height /= 2;
      case LEFT -> bounds.width /= 2;
      case BOTTOM -> {
        int h = bounds.height / 2;
        bounds.height -= h;
        bounds.y += h;
      }
      case RIGHT -> {
        int w = bounds.width / 2;
        bounds.width -= w;
        bounds.x += w;
      }
    }
  }
}
