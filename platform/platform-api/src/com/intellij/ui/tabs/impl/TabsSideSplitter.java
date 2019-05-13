/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
