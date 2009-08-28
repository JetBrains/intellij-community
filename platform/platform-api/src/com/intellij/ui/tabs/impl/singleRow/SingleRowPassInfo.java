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
