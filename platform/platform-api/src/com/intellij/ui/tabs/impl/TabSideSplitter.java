// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl;

import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.Splittable;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

class TabSideSplitter implements Splittable, PropertyChangeListener {
  private final @NotNull JBTabsImpl tabs;
  private int sideTabsLimit = JBTabsImpl.DEFAULT_MAX_TAB_WIDTH;
  private final OnePixelDivider divider;

  TabSideSplitter(@NotNull JBTabsImpl tabs) {
    this.tabs = tabs;
    this.tabs.addPropertyChangeListener(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString(), this);
    divider = new OnePixelDivider(false, this);
  }

  OnePixelDivider getDivider() {
    return divider;
  }

  @Override
  public float getMinProportion(boolean first) {
    return Math.min(.5F, (float)JBTabsImpl.MIN_TAB_WIDTH / Math.max(1, tabs.getWidth()));
  }

  @Override
  public void setProportion(float proportion) {
    int width = tabs.getWidth();
    if (tabs.getTabsPosition() == JBTabsPosition.left) {
      setSideTabsLimit((int)Math.max(JBTabsImpl.MIN_TAB_WIDTH, proportion * width));
    }
    else if (tabs.getTabsPosition() == JBTabsPosition.right) {
      setSideTabsLimit(width - (int)Math.max(JBTabsImpl.MIN_TAB_WIDTH, proportion * width));
    }
  }

  int getSideTabsLimit() {
    return sideTabsLimit;
  }

  void setSideTabsLimit(int sideTabsLimit) {
    if (this.sideTabsLimit != sideTabsLimit) {
      this.sideTabsLimit = sideTabsLimit;
      tabs.putClientProperty(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY, this.sideTabsLimit);
      tabs.relayout(true, true);
      TabInfo info = tabs.getSelectedInfo();
      JComponent page = info != null ? info.getComponent() : null;
      if (page != null) {
        page.revalidate();
        page.repaint();
      }
    }
  }

  @Override
  public boolean getOrientation() {
    return false;
  }

  @Override
  public void setOrientation(boolean verticalSplit) {
    //ignore
  }

  @Override
  public void setDragging(boolean dragging) {
    // ignore
  }

  @Override
  public @NotNull Component asComponent() {
    return tabs;
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (event.getSource() != tabs) {
      return;
    }
    Integer limit = ClientProperty.get(tabs, JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY);
    if (limit == null) {
      limit = JBTabsImpl.DEFAULT_MAX_TAB_WIDTH;
    }
    setSideTabsLimit(limit);
  }
}
