// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl;

import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.Splittable;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;


class TabsSideSplitter implements Splittable, PropertyChangeListener {

  @NotNull private final JBTabsImpl myTabs;
  private int mySideTabsLimit = JBTabsImpl.DEFAULT_MAX_TAB_WIDTH;
  private boolean myDragging;
  private final OnePixelDivider myDivider;


  TabsSideSplitter(@NotNull JBTabsImpl tabs) {
    myTabs = tabs;
    myTabs.addPropertyChangeListener(JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY.toString(), this);
    myDivider = new OnePixelDivider(false, this);
  }

  OnePixelDivider getDivider() {
    return myDivider;
  }

  @Override
  public float getMinProportion(boolean first) {
    return Math.min(.5F, (float)JBTabsImpl.MIN_TAB_WIDTH / Math.max(1, myTabs.getWidth()));
  }

  @Override
  public void setProportion(float proportion) {
    int width = myTabs.getWidth();
    if (myTabs.getTabsPosition() == JBTabsPosition.left) {
      setSideTabsLimit((int)Math.max(JBTabsImpl.MIN_TAB_WIDTH, proportion * width));
    }
    else if (myTabs.getTabsPosition() == JBTabsPosition.right) {
      setSideTabsLimit(width - (int)Math.max(JBTabsImpl.MIN_TAB_WIDTH, proportion * width));
    }
  }

  int getSideTabsLimit() {
    return mySideTabsLimit;
  }

  void setSideTabsLimit(int sideTabsLimit) {
    if (mySideTabsLimit != sideTabsLimit) {
      mySideTabsLimit = sideTabsLimit;
      UIUtil.putClientProperty(myTabs, JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY, mySideTabsLimit);
      myTabs.resetLayout(true);
      myTabs.doLayout();
      myTabs.repaint();
      TabInfo info = myTabs.getSelectedInfo();
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
    myDragging = dragging;
  }

  boolean isDragging() {
    return myDragging;
  }

  @NotNull
  @Override
  public Component asComponent() {
    return myTabs;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt.getSource() != myTabs) return;
    Integer limit = UIUtil.getClientProperty(myTabs, JBTabsImpl.SIDE_TABS_SIZE_LIMIT_KEY);
    if (limit == null) limit = JBTabsImpl.DEFAULT_MAX_TAB_WIDTH;
    setSideTabsLimit(limit);
  }
}
