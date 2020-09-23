// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class TabLayout {

  public ShapeTransform createShapeTransform(Dimension dimension) {
    return createShapeTransform(new Rectangle(0, 0, dimension.width, dimension.height));
  }

  public ShapeTransform createShapeTransform(Rectangle rectangle) {
    return new ShapeTransform.Top(rectangle);
  }

  public boolean isDragOut(@NotNull TabLabel tabLabel, int deltaX, int deltaY) {
    return Math.abs(deltaY) > tabLabel.getHeight() * getDragOutMultiplier()
           || Math.abs(deltaX) > tabLabel.getWidth() * getDragOutMultiplier();
  }

  public boolean isSideComponentOnTabs() {
    return false;
  }

  public static double getDragOutMultiplier() {
    return Registry.doubleValue("ide.tabbedPane.dragOutMultiplier");
  }

  public abstract int getDropIndexFor(Point point);

  public static int getMaxPinnedTabWidth() {
    return JBUI.scale(Registry.intValue("ide.editor.max.pinned.tab.width", 100));
  }

  @MagicConstant(intValues = {SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT, -1})
  public abstract int getDropSideFor(@NotNull Point point);
}
