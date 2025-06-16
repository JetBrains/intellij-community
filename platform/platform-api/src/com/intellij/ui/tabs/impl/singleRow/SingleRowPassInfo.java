// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class SingleRowPassInfo extends LayoutPassInfo {
  private final JBTabsImpl tabs;
  final Dimension layoutSize;
  final int contentCount;
  int position;
  int requiredLength;
  int toFitLength;
  public final List<TabInfo> toLayout;
  public final List<TabInfo> toDrop;
  final int entryPointAxisSize;
  final int moreRectAxisSize;

  public WeakReference<JComponent> hfToolbar;
  public WeakReference<JComponent> hToolbar;
  public WeakReference<JComponent> vToolbar;

  public Insets insets;

  public WeakReference<JComponent> component;
  public Rectangle tabRectangle;
  final int scrollOffset;

  public SingleRowPassInfo(SingleRowLayout layout, List<TabInfo> visibleInfos) {
    super(visibleInfos);
    tabs = layout.tabs;
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
  public @NotNull Rectangle getHeaderRectangle() {
    return (Rectangle)tabRectangle.clone();
  }

  @Override
  public int getRequiredLength() {
    return requiredLength;
  }

  @Override
  public int getScrollExtent() {
    if (tabs.isHorizontalTabs()) {
      return !moreRect.isEmpty() ? moreRect.x - tabs.getActionsInsets().left
             : !entryPointRect.isEmpty() ? entryPointRect.x - tabs.getActionsInsets().left
             : layoutSize.width;
    }
    else {
      if (ExperimentalUI.isNewUI()) {
        return layoutSize.height;
      }
      return !moreRect.isEmpty() ? moreRect.y - tabs.getActionsInsets().top
             : !entryPointRect.isEmpty() ? entryPointRect.y - tabs.getActionsInsets().top
             : layoutSize.height;
    }
  }
}
