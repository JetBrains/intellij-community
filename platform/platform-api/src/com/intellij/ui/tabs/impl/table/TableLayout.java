// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.table;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.TabLayout;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class TableLayout extends TabLayout {
  private int myScrollOffset = 0;
  private boolean myScrollSelectionInViewPending = false;

  final JBTabsImpl myTabs;

  public TablePassInfo myLastTableLayout;

  public TableLayout(final JBTabsImpl tabs) {
    myTabs = tabs;
  }

  private TablePassInfo computeLayoutTable(List<TabInfo> visibleInfos) {
    final TablePassInfo data = new TablePassInfo(this, visibleInfos);
    if (myTabs.isHideTabs()) {
      return data;
    }
    boolean singleRow = myTabs.isSingleRow();
    boolean showPinnedTabsSeparately = UISettings.getInstance().getState().getShowPinnedTabsInASeparateRow();
    boolean scrollable = UISettings.getInstance().getHideTabsIfNeeded() && singleRow;
    int titleWidth = myTabs.myTitleWrapper.getPreferredSize().width;

    data.titleRect.setBounds(data.toFitRec.x, data.toFitRec.y, titleWidth, myTabs.myHeaderFitSize.height);
    data.entryPointRect.setBounds(data.toFitRec.x + data.toFitRec.width, data.toFitRec.y, myTabs.getEntryPointPreferredSize().width, myTabs.myHeaderFitSize.height);
    data.moreRect.setBounds(data.toFitRec.x + data.toFitRec.width, data.toFitRec.y, 0, myTabs.myHeaderFitSize.height);
    calculateLengths(data);

    int eachX = data.titleRect.x + data.titleRect.width;
    Insets insets = myTabs.getLayoutInsets();
    int eachY = insets.top;
    int requiredRowsPinned = 0;
    int requiredRowsUnpinned = 0;

    final int maxX = data.moreRect.x - 1;
    ActionToolbar entryPointToolbar = myTabs.myEntryPointToolbar;

    int hGap = myTabs.getTabHGap();
    int entryPointMargin = entryPointToolbar != null ? entryPointToolbar.getComponent().getPreferredSize().width : 0;
    for (TabInfo eachInfo : data.myVisibleInfos) {
      TabLabel eachLabel = myTabs.getTabLabel(eachInfo);
      boolean pinned = eachLabel.isPinned();
      int width = data.lengths.get(eachInfo);
      if (!pinned || !showPinnedTabsSeparately) {
        data.requiredLength += width;
      }
      if (pinned && showPinnedTabsSeparately) {
        if (requiredRowsPinned == 0) {
          requiredRowsPinned = 1;
        }
        myTabs.layout(eachLabel, eachX, eachY, width, myTabs.myHeaderFitSize.height);
        data.bounds.put(eachInfo, eachLabel.getBounds());
      }
      else {
        if ((!scrollable && eachX + width + hGap > maxX - entryPointMargin && !singleRow) || (showPinnedTabsSeparately && eachLabel.isNextToLastPinned())) {
          requiredRowsUnpinned++;
          eachY += myTabs.myHeaderFitSize.height;
          eachX = data.toFitRec.x;
        }
        else if (requiredRowsUnpinned == 0) {
          requiredRowsUnpinned = 1;
        }
        if (scrollable) {
          if (eachX - getScrollOffset() + width + hGap > maxX - entryPointMargin) {
            width = Math.max(0, maxX - eachX + getScrollOffset());
            data.invisible.add(eachInfo);
          }
        }

        myTabs.layout(eachLabel, eachX - getScrollOffset(), eachY, width == 1 ? 0 : width, myTabs.myHeaderFitSize.height);
        Rectangle rectangle = new Rectangle(myTabs.myHeaderFitSize);
        data.bounds.put(eachInfo, eachLabel.getBounds());
        int intersection = eachLabel.getBounds().intersection(rectangle).width;
        if (scrollable && intersection < eachLabel.getBounds().width) {
          data.invisible.add(eachInfo);
        }
      }
      eachX += width + hGap;
      if (requiredRowsPinned + requiredRowsUnpinned > 1) {
        entryPointMargin = singleRow ? 0 : - data.moreRect.width;
      }
    }
    if (requiredRowsPinned > 0 && requiredRowsUnpinned > 0) data.moreRect.y += myTabs.myHeaderFitSize.height /*+ myTabs.getSeparatorWidth()*/;

    if (data.invisible.isEmpty()) {
      data.moreRect.setBounds(0, 0, 0, 0);
    }

    eachY = -1;
    TableRow eachTableRow = new TableRow(data);

    for (TabInfo eachInfo : data.myVisibleInfos) {
      final TabLabel eachLabel = myTabs.getTabLabel(eachInfo);
      if (eachY == -1 || eachY != eachLabel.getY()) {
        if (eachY != -1) {
          eachTableRow = new TableRow(data);
        }
        eachY = eachLabel.getY();
        data.table.add(eachTableRow);
      }
      eachTableRow.add(eachInfo, eachLabel.getWidth());
    }
    if (myScrollSelectionInViewPending) {
      myScrollSelectionInViewPending = false;
      doScrollSelectionInView(data);
    }
    clampScrollOffsetToBounds(data);
    return data;
  }

  private void calculateLengths(TablePassInfo data) {
    boolean compressible = isCompressible();
    boolean showPinnedTabsSeparately = UISettings.getInstance().getState().getShowPinnedTabsInASeparateRow();

    int standardLengthToFit = data.moreRect.x - (data.titleRect.x + data.titleRect.width);
    if (compressible || showPinnedTabsSeparately) {
      if (showPinnedTabsSeparately) {
        List<TabInfo> pinned = ContainerUtil.filter(data.myVisibleInfos, info -> info.isPinned());
        calculateCompressibleLengths(pinned, data, standardLengthToFit + data.moreRect.width - data.entryPointRect.width);
        List<TabInfo> unpinned = ContainerUtil.filter(data.myVisibleInfos, info -> !info.isPinned());
        if (compressible) {
          calculateCompressibleLengths(unpinned, data, pinned.isEmpty()
                                                       ? standardLengthToFit + data.moreRect.width
                                                       : standardLengthToFit + data.titleRect.width + data.moreRect.width);
        }
        else {
          calculateRawLengths(unpinned, data);
          if (getTotalLength(unpinned, data) > standardLengthToFit) {
            int moreWidth = myTabs.isSingleRow() ? myTabs.myMoreToolbar.getComponent().getPreferredSize().width : 0;
            int entryPointsWidth = pinned.isEmpty() ? myTabs.getEntryPointPreferredSize().width : 0;
            data.moreRect.setBounds(data.toFitRec.x + data.toFitRec.width - moreWidth - entryPointsWidth, /*data.toFitRec.y*/myTabs.getLayoutInsets().top, moreWidth, myTabs.myHeaderFitSize.height /*- myTabs.getSeparatorWidth()*/);
            calculateRawLengths(unpinned, data);
          }
        }
      }
      else {
        calculateCompressibleLengths(data.myVisibleInfos, data, standardLengthToFit);
      }
    }
    else {//both scrollable and multi-row
      calculateRawLengths(data.myVisibleInfos, data);
      if (getTotalLength(data.myVisibleInfos, data) > standardLengthToFit) {
        int moreWidth = myTabs.isSingleRow() ? myTabs.myMoreToolbar.getComponent().getPreferredSize().width : 0;
        data.moreRect.setBounds(data.toFitRec.x + data.toFitRec.width - moreWidth, data.toFitRec.y, moreWidth, myTabs.myHeaderFitSize.height);
        calculateRawLengths(data.myVisibleInfos, data);
      }
    }
  }

  private static int getTotalLength(@NotNull List<TabInfo> list, @NotNull TablePassInfo data) {
    int total = 0;
    for (TabInfo info : list) {
      total += data.lengths.get(info);
    }
    return total;
  }

  private boolean isCompressible() {
    return myTabs.isSingleRow() && !UISettings.getInstance().getHideTabsIfNeeded() && myTabs.supportsCompression();
  }

  private void calculateCompressibleLengths(List<TabInfo> list, TablePassInfo data, int toFitLength) {
    if (list.isEmpty()) return;
    int spentLength = 0;
    int lengthEstimation = 0;

    for (TabInfo tabInfo : list) {
      lengthEstimation += Math.max(getMinTabWidth(), myTabs.myInfo2Label.get(tabInfo).getPreferredSize().width);
    }

    final int extraWidth = toFitLength - lengthEstimation;

    for (Iterator<TabInfo> iterator = list.iterator(); iterator.hasNext(); ) {
      TabInfo tabInfo = iterator.next();
      final TabLabel label = myTabs.myInfo2Label.get(tabInfo);

      int length;
      int lengthIncrement = label.getPreferredSize().width;
      if (!iterator.hasNext()) {
        length = Math.min(toFitLength - spentLength, lengthIncrement);
      }
      else if (extraWidth <= 0) {//need compress
        length = (int)(lengthIncrement * (float)toFitLength / lengthEstimation);
      }
      else {
        length = lengthIncrement;
      }
      if (tabInfo.isPinned()) {
        length = Math.min(getMaxPinnedTabWidth(), length);
      }
      length = Math.max(getMinTabWidth(), length);
      data.lengths.put(tabInfo, length);
      spentLength += length + myTabs.getTabHGap();
    }
  }

  private void calculateRawLengths(List<TabInfo> list, TablePassInfo data) {
    boolean showPinnedTabsSeparately = UISettings.getInstance().getState().getShowPinnedTabsInASeparateRow();
    for (TabInfo info : list) {
      TabLabel eachLabel = myTabs.getTabLabel(info);
      Dimension size =
        eachLabel.isPinned() && showPinnedTabsSeparately ? eachLabel.getNotStrictPreferredSize() : eachLabel.getPreferredSize();
      data.lengths.put(info, Math.max(getMinTabWidth(), size.width + myTabs.getTabHGap()));
    }
  }

  public LayoutPassInfo layoutTable(List<TabInfo> visibleInfos, JComponent title, JComponent moreToolbar) {
    myTabs.resetLayout(true);
    Rectangle unitedTabArea = null;
    TablePassInfo data = computeLayoutTable(visibleInfos);
    Rectangle rect = new Rectangle(data.moreRect);
    rect.y += myTabs.getBorderThickness();
    moreToolbar.setBounds(rect);
    title.setBounds(data.titleRect);
    Insets insets = myTabs.getLayoutInsets();
    int eachY = insets.top;
    for (TabInfo info : visibleInfos) {
      Rectangle bounds = data.bounds.get(info);
      if (unitedTabArea == null) {
        unitedTabArea = bounds;
      }
      else {
        unitedTabArea = unitedTabArea.union(bounds);
      }
    }

    if (myTabs.getSelectedInfo() != null) {
      final JBTabsImpl.Toolbar selectedToolbar = myTabs.myInfo2Toolbar.get(myTabs.getSelectedInfo());

      final int componentY = (unitedTabArea != null ? unitedTabArea.y + unitedTabArea.height : eachY) + (myTabs.isEditorTabs() ? 0 : 2) -
                             myTabs.getLayoutInsets().top;
      if (!myTabs.myHorizontalSide && selectedToolbar != null && !selectedToolbar.isEmpty()) {
        final int toolbarWidth = selectedToolbar.getPreferredSize().width;
        final int vSeparatorWidth = toolbarWidth > 0 ? myTabs.getSeparatorWidth() : 0;
        if (myTabs.isSideComponentBefore()) {
          Rectangle compRect =
            myTabs.layoutComp(toolbarWidth + vSeparatorWidth, componentY, myTabs.getSelectedInfo().getComponent(), 0, 0);
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
    if (unitedTabArea != null) {
      data.tabRectangle.setBounds(unitedTabArea);
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
    Set<TabInfo> lastInRow = new HashSet<>();
    for (int i = 0; i < myLastTableLayout.table.size(); i++) {
      List<TabInfo> columns = myLastTableLayout.table.get(i).myColumns;
      if (!columns.isEmpty()) {
        lastInRow.add(columns.get(columns.size() - 1));
      }
    }

    if (c instanceof JBTabsImpl) {
      for (int i = 0; i < myLastTableLayout.myVisibleInfos.size() - 1; i++) {
        TabInfo firstInfo = myLastTableLayout.myVisibleInfos.get(i);
        TabInfo secondInfo = myLastTableLayout.myVisibleInfos.get(i + 1);
        TabLabel first = myTabs.myInfo2Label.get(firstInfo);
        TabLabel second = myTabs.myInfo2Label.get(secondInfo);

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
        if (lastInRow.contains(firstInfo)
            && firstBounds.y <= point.y
            && firstBounds.getMaxY() >= point.y
            && firstBounds.getMaxX() <= point.x) {
          c = second;
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
      }
      else if (index < myLastTableLayout.myVisibleInfos.size()) {
        result = index;
      }
    }
    return result;
  }

  @Override
  @MagicConstant(intValues = {
    SwingConstants.CENTER,
    SwingConstants.TOP,
    SwingConstants.LEFT,
    SwingConstants.BOTTOM,
    SwingConstants.RIGHT,
    -1
  })
  public int getDropSideFor(@NotNull Point point) {
    return TabsUtil.getDropSideFor(point, myTabs);
  }

  int getScrollOffset() {
    return myScrollOffset;
  }

  public void scroll(int units) {
    if (!myTabs.isSingleRow()) {
      myScrollOffset = 0;
      return;
    }
    myScrollOffset += units;

    clampScrollOffsetToBounds(myLastTableLayout);
  }

  private void clampScrollOffsetToBounds(@Nullable TablePassInfo data) {
    if (data == null) {
      return;
    }
    if (data.requiredLength < data.toFitRec.width) {
      myScrollOffset = 0;
    }
    else {
      int entryPointsWidth = data.moreRect.y == data.entryPointRect.y ? data.entryPointRect.width + 1 : 0;
      myScrollOffset = Math.max(0, Math.min(myScrollOffset,
                                            data.requiredLength - data.toFitRec.width + data.moreRect.width + entryPointsWidth /*+ (1 + myTabs.getIndexOf(myTabs.getSelectedInfo())) * myTabs.getBorderThickness()*/+ data.titleRect.width));
    }
  }

  public int getScrollUnitIncrement() {
    return 10;
  }

  public void scrollSelectionInView() {
    myScrollSelectionInViewPending = true;
    doScrollSelectionInView(myLastTableLayout);
  }

  private void doScrollSelectionInView(TablePassInfo data) {
    if (myTabs.isMouseInsideTabsArea()
        || data == null
        || data.lengths.isEmpty()
        || myTabs.isHideTabs()
        || !UISettings.getInstance().getState().getShowPinnedTabsInASeparateRow()) {
      return;
    }

    int offset = -myScrollOffset;
    for (TabInfo info : data.myVisibleInfos) {
      if (info.isPinned()) continue;
      final int length = data.lengths.get(info);
      if (info == myTabs.getSelectedInfo()) {
        if (offset < 0) {
          scroll(offset);
        }
        else {
          final int maxLength = data.moreRect.x;
          if (offset + length > maxLength) {
            // left side should be always visible
            if (length < maxLength) {
              scroll(offset + length - maxLength);
            }
            else {
              scroll(offset);
            }
          }
        }
        break;
      }
      offset += length;
    }
  }
}
