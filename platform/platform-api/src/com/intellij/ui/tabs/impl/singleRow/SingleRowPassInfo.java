// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class SingleRowPassInfo extends LayoutPassInfo {
  final Dimension layoutSize;
  final int contentCount;
  int position;
  int requiredLength;
  int toFitLength;
  public final List<TabInfo> toLayout;
  public final List<TabInfo> toDrop;
  final int moreRectAxisSize;
  public Rectangle moreRect;
  public Rectangle titleRect;

  public WeakReference<JComponent> hToolbar;
  public WeakReference<JComponent> vToolbar;

  public Insets insets;

  public WeakReference<JComponent> comp;
  public Rectangle tabRectangle;
  final int scrollOffset;


  public SingleRowPassInfo(SingleRowLayout layout, List<TabInfo> visibleInfos) {
    super(visibleInfos);
    JBTabsImpl tabs = layout.myTabs;
    layoutSize = tabs.getSize();
    contentCount = tabs.getTabCount();
    toLayout = new ArrayList<>();
    toDrop = new ArrayList<>();
    moreRectAxisSize = layout.getStrategy().getMoreRectAxisSize();
    scrollOffset = layout.getScrollOffset();
  }

  @Deprecated
  @Override
  public int getRowCount() {
    return 1;
  }

  @Deprecated
  @Override
  public int getColumnCount(final int row) {
    return myVisibleInfos.size();
  }

  @Deprecated
  @Override
  public TabInfo getTabAt(final int row, final int column) {
    return myVisibleInfos.get(column);
  }

  @Override
  public Rectangle getHeaderRectangle() {
    return (Rectangle)tabRectangle.clone();
  }
}
