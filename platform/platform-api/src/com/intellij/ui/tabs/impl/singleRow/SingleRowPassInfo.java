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
  final int entryPointAxisSize;
  final int moreRectAxisSize;
  public Rectangle entryPointRect;
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
    entryPointAxisSize = layout.getStrategy().getEntryPointAxisSize();
    moreRectAxisSize = layout.getStrategy().getMoreRectAxisSize();
    scrollOffset = layout.getScrollOffset();
  }

  @Override
  public int getRowCount() {
    return 1;
  }

  @Override
  public Rectangle getHeaderRectangle() {
    return (Rectangle)tabRectangle.clone();
  }

  @Override
  public int getRequiredLength() {
    return requiredLength;
  }
}
