package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.tabs.impl.Layout;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.TabInfo;

import java.awt.*;
import java.util.ArrayList;

public class LineLayoutData extends Layout {
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

  public LineLayoutData(SingleRowLayout layout) {
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
}
