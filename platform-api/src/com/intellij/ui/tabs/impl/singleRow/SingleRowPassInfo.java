package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.tabs.impl.LayoutPassInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.TabInfo;

import java.awt.*;
import java.util.ArrayList;

public class SingleRowPassInfo extends LayoutPassInfo {
  final Dimension laayoutSize;
  int contentCount;
  int eachX;
  int xAddin;
  int requiredWidth;
  int toFitWidth;
  public final java.util.List<TabInfo> toLayout;
  public final java.util.List<TabInfo> toDrop;
  int moreRectWidth;
  public Rectangle moreRect;
  public boolean displayedHToolbar;
  public int yComp;

  public Rectangle leftGhost;
  public boolean leftGhostVisible;

  public Rectangle rightGhost;
  public boolean rightGhostVisible;

  public Insets insets;

  private JBTabsImpl myTabs;

  public SingleRowPassInfo(SingleRowLayout layout) {
    myTabs = layout.myTabs;
    laayoutSize = layout.myTabs.getSize();
    contentCount = myTabs.getTabCount();
    toLayout = new ArrayList<TabInfo>();
    toDrop = new ArrayList<TabInfo>();
    moreRectWidth = layout.myMoreIcon.getIconWidth() + 6;
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

  public boolean hasCurveSpaceFor(final TabInfo tabInfo) {
    return true;
  }
}
