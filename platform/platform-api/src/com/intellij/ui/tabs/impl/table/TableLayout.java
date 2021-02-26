// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.table;

import com.intellij.ide.ui.UISettings;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.TabLayout;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class TableLayout extends TabLayout {

  private final JBTabsImpl myTabs;

  public TablePassInfo myLastTableLayout;

  public TableLayout(final JBTabsImpl tabs) {
    myTabs = tabs;
  }

  private TablePassInfo computeLayoutTable(List<TabInfo> visibleInfos) {
    final TablePassInfo data = new TablePassInfo(myTabs, visibleInfos);

    int eachXPinned = data.toFitRec.x;
    int eachXUnpinned = data.toFitRec.x;
    TableRow eachTableRow = new TableRow(data);
    data.table.add(eachTableRow);
    int requiredRowsPinned = 0;
    int requiredRowsUnpinned = 0;


    final int maxX = data.toFitRec.x + data.toFitRec.width;

    int hGap = myTabs.getTabHGap();
    boolean showPinnedTabsSeparately = UISettings.getInstance().getState().getShowPinnedTabsInASeparateRow();
    for (TabInfo eachInfo : data.myVisibleInfos) {
      final TabLabel eachLabel = myTabs.myInfo2Label.get(eachInfo);
      boolean pinned = eachLabel.isPinned();
      final Dimension size = eachLabel.getNotStrictPreferredSize();
      int width = size.width + hGap;
      if (pinned && showPinnedTabsSeparately) {
        if (eachXPinned + width >= maxX) {
          requiredRowsPinned++;
          eachXPinned = data.toFitRec.x;
        }
        else if (requiredRowsPinned == 0) {
          requiredRowsPinned = 1;
        }
        myTabs.layout(eachLabel, eachXPinned, 0, size.width, 1);
        eachXPinned += width;
      }
      else {
        if (eachXUnpinned + size.width + hGap >= maxX) {
          requiredRowsUnpinned++;
          eachXUnpinned = data.toFitRec.x;
        }
        else if (requiredRowsUnpinned == 0) {
          requiredRowsUnpinned = 1;
        }
        myTabs.layout(eachLabel, eachXUnpinned, 0, size.width, 1);
        eachXUnpinned += width;
      }
    }

    eachXPinned = data.toFitRec.x;
    eachXUnpinned = data.toFitRec.x;

    for (TabInfo eachInfo : data.myVisibleInfos) {
      final TabLabel eachLabel = myTabs.myInfo2Label.get(eachInfo);
      final Dimension size = eachLabel.getNotStrictPreferredSize();
      boolean pinned = eachLabel.isPinned();
      int width = size.width + hGap;
      if (pinned && showPinnedTabsSeparately) {
        if (eachXPinned + width <= maxX) {
          eachTableRow.add(eachInfo, width);
          eachXPinned += width;
        } else {
          eachTableRow = new TableRow(data);
          data.table.add(eachTableRow);
          eachXPinned = data.toFitRec.x + width;
          eachTableRow.add(eachInfo, width);
        }
      } else {
        if (eachXUnpinned + size.width + hGap <= maxX && (!showPinnedTabsSeparately || !eachLabel.isNextToLastPinned())) {
          eachTableRow.add(eachInfo, width);
          eachXUnpinned += width;
        } else {
          eachTableRow = new TableRow(data);
          data.table.add(eachTableRow);
          eachXUnpinned = data.toFitRec.x + width;
          eachTableRow.add(eachInfo, width);
        }
      }
    }

    return data;
  }
                                           
  public boolean isLastRow(TabInfo info) {
    if (info == null) return false;
    List<TableRow> rows = myLastTableLayout.table;
    if (rows.size() > 0) {
      for (TabInfo tabInfo : rows.get(rows.size() - 1).myColumns) {
        if (tabInfo == info) return true;
      }
    }
    
    return false; 
  }

  public LayoutPassInfo layoutTable(List<TabInfo> visibleInfos) {
    myTabs.resetLayout(true);
    Insets insets = myTabs.getLayoutInsets();
    int eachY = insets.top;
    TablePassInfo data = new TablePassInfo(myTabs, visibleInfos);
    boolean showPinnedTabsSeparately = UISettings.getInstance().getState().getShowPinnedTabsInASeparateRow();

    if (!myTabs.isHideTabs()) {
      data = computeLayoutTable(visibleInfos);
      insets = myTabs.getLayoutInsets();
      eachY = insets.top;
      int eachX;

      for (TableRow eachRow : data.table) {
        eachX = insets.left;

        for (int i = 0; i < eachRow.myColumns.size(); i++) {
          TabInfo tabInfo = eachRow.myColumns.get(i);
          final TabLabel label = myTabs.myInfo2Label.get(tabInfo);

          int width;
          if (label.isPinned() && showPinnedTabsSeparately) {
            width = label.getNotStrictPreferredSize().width;
          }
          else {
            width = label.getPreferredSize().width;
          }

          myTabs.layout(label, eachX, eachY, width, myTabs.myHeaderFitSize.height);
          label.setAlignmentToCenter(false);

          boolean lastCell = i == eachRow.myColumns.size() - 1;
          eachX += width + (lastCell ? 0 : myTabs.getTabHGap());
        }
        eachY += myTabs.myHeaderFitSize.height;
      }
    }

    if (myTabs.getSelectedInfo() != null) {
      final JBTabsImpl.Toolbar selectedToolbar = myTabs.myInfo2Toolbar.get(myTabs.getSelectedInfo());

      final int componentY = eachY + (myTabs.isEditorTabs() ? 0 : 2) - myTabs.getLayoutInsets().top;
      if (!myTabs.myHorizontalSide && selectedToolbar != null && !selectedToolbar.isEmpty()) {
        final int toolbarWidth = selectedToolbar.getPreferredSize().width;
        final int vSeparatorWidth = toolbarWidth > 0 ? myTabs.getSeparatorWidth() : 0;
        if (myTabs.isSideComponentBefore()) {
          Rectangle compRect = myTabs.layoutComp(toolbarWidth + vSeparatorWidth, componentY, myTabs.getSelectedInfo().getComponent(), 0, 0);
          myTabs.layout(selectedToolbar, compRect.x - toolbarWidth - vSeparatorWidth, compRect.y, toolbarWidth, compRect.height);
        }
        else {
          final int width = myTabs.getWidth() - toolbarWidth - vSeparatorWidth;
          Rectangle compRect = myTabs.layoutComp(new Rectangle(0, componentY, width, myTabs.getHeight()),
                                                 myTabs.getSelectedInfo().getComponent(), 0, 0);
          myTabs.layout(selectedToolbar, compRect.x + compRect.width + vSeparatorWidth, compRect.y, toolbarWidth, compRect.height);
        }
      }
      else {
        myTabs.layoutComp(0, componentY, myTabs.getSelectedInfo().getComponent(), 0, 0);
      }
    }

    myLastTableLayout = data;
    return data;
  }

  @Override
  public boolean isDragOut(@NotNull TabLabel tabLabel, int deltaX, int deltaY) {
    if (myLastTableLayout == null) {
      return super.isDragOut(tabLabel, deltaX, deltaY);
    }

    Rectangle area = new Rectangle(myLastTableLayout.toFitRec.width, tabLabel.getBounds().height);
    for (int i = 0; i < myLastTableLayout.myVisibleInfos.size(); i++) {
      area = area.union(myTabs.myInfo2Label.get(myLastTableLayout.myVisibleInfos.get(i)).getBounds());
    }
    return Math.abs(deltaY) > area.height * getDragOutMultiplier();
  }

  @Override
  public int getDropIndexFor(Point point) {
    if (myLastTableLayout == null) return -1;
    int result = -1;

    Component c = myTabs.getComponentAt(point);

    if (c instanceof JBTabsImpl) {
      for (int i = 0; i < myLastTableLayout.myVisibleInfos.size() - 1; i++) {
        TabLabel first = myTabs.myInfo2Label.get(myLastTableLayout.myVisibleInfos.get(i));
        TabLabel second = myTabs.myInfo2Label.get(myLastTableLayout.myVisibleInfos.get(i + 1));

        Rectangle firstBounds = first.getBounds();
        Rectangle secondBounds = second.getBounds();

        final boolean between = firstBounds.getMaxX() < point.x
                    && secondBounds.getX() > point.x
                    && firstBounds.y < point.y
                    && secondBounds.getMaxY() > point.y;

        if (between) {
          c = first;
          break;
        }
      }
    }

    if (c instanceof TabLabel) {
      TabInfo info = ((TabLabel)c).getInfo();
      int index = myLastTableLayout.myVisibleInfos.indexOf(info);
      boolean isDropTarget = myTabs.isDropTarget(info);
      if (!isDropTarget) {
        for (int i = 0; i <= index; i++) {
          if (myTabs.isDropTarget(myLastTableLayout.myVisibleInfos.get(i))) {
            index -= 1;
            break;
          }
        }
        result = index;
      } else if (index < myLastTableLayout.myVisibleInfos.size()) {
        result = index;
      }
    }
    return result;
  }

  @Override
  @MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT, -1})
  public int getDropSideFor(@NotNull Point point) {
    return TabsUtil.getDropSideFor(point, myTabs);
  }
}
