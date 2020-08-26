// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.tabsLayout;

import com.intellij.openapi.Disposable;
import com.intellij.ui.tabs.impl.LayoutPassInfo;
import com.intellij.ui.tabs.impl.TabLabel;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;

public interface TabsLayout extends Disposable {

  void init(@NotNull TabsLayoutCallback callback);

  LayoutPassInfo layoutContainer(boolean isForced);

  @Nullable
  MouseListener getMouseListener();

  @Nullable
  MouseMotionListener getMouseMotionListener();

  @Nullable
  MouseWheelListener getMouseWheelListener();

  int getDropIndexFor(Point point);

  @MagicConstant(intValues = {SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT, -1})
  int getDropSideFor(Point point);

  void mouseMotionEventDispatched(MouseEvent mouseMotionEvent);

  boolean isToolbarOnTabs();

  boolean isSingleRow();

  boolean isDragOut(TabLabel tabLabel, int deltaX, int deltaY);

  boolean ignoreTabLabelLimitedWidthWhenPaint();
}
