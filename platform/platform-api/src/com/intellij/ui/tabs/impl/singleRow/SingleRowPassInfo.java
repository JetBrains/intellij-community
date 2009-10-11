/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class SingleRowPassInfo extends LayoutPassInfo {
  final Dimension laayoutSize;
  int contentCount;
  int position;
  int requiredLength;
  int toFitLength;
  public final java.util.List<TabInfo> toLayout;
  public final java.util.List<TabInfo> toDrop;
  int moreRectAxisSize;
  public Rectangle moreRect;

  public JComponent hToolbar;
  public JComponent vToolbar;

  public int compPosition;

  public Rectangle firstGhost;
  public boolean firstGhostVisible;

  public Rectangle lastGhost;
  public boolean lastGhostVisible;

  public Insets insets;

  private final JBTabsImpl myTabs;
  public JComponent comp;
  public Rectangle tabRectangle;

  public SingleRowPassInfo(SingleRowLayout layout) {
    myTabs = layout.myTabs;
    laayoutSize = layout.myTabs.getSize();
    contentCount = myTabs.getTabCount();
    toLayout = new ArrayList<TabInfo>();
    toDrop = new ArrayList<TabInfo>();
    moreRectAxisSize = layout.getStrategy().getMoreRectAxisSize();
  }

  public TabInfo getPreviousFor(final TabInfo info) {
    return getPrevious(myTabs.myVisibleInfos, myTabs.myVisibleInfos.indexOf(info));
  }

  public TabInfo getNextFor(final TabInfo info) {
    return getNext(myTabs.myVisibleInfos, myTabs.myVisibleInfos.indexOf(info));
  }

  public int getRowCount() {
    return 1;
  }

  public int getColumnCount(final int row) {
    return myTabs.myVisibleInfos.size();
  }

  public TabInfo getTabAt(final int row, final int column) {
    return myTabs.myVisibleInfos.get(column);
  }

  public Rectangle getHeaderRectangle() {
    return (Rectangle)tabRectangle.clone();
  }

  public boolean hasCurveSpaceFor(final TabInfo tabInfo) {
    return true;
  }
}
