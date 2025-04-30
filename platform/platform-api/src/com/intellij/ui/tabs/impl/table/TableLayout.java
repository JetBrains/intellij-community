// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @deprecated use inheritors of {@link com.intellij.ui.tabs.impl.multiRow.MultiRowLayout} instead
 */
@SuppressWarnings("removal")
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public class TableLayout extends TabLayout {
  private int myScrollOffset = 0;

  final JBTabsImpl myTabs;

  public TablePassInfo lastTableLayout;

  private final boolean myWithScrollBar;

  public TableLayout(final JBTabsImpl tabs) {
    this(tabs, false);
  }

  public TableLayout(final JBTabsImpl tabs, boolean isWithScrollBar) {
    myTabs = tabs;
    myWithScrollBar = isWithScrollBar;
  }

  private TablePassInfo computeLayoutTable(List<TabInfo> visibleInfos) {
    final TablePassInfo data = new TablePassInfo(this, visibleInfos);
    if (myTabs.isHideTabs()) {
      return data;
    }
    doScrollToSelectedTab(lastTableLayout);

    boolean singleRow = myTabs.isSingleRow();
    boolean showPinnedTabsSeparately = showPinnedTabsSeparately();
    boolean scrollable = UISettings.getInstance().getHideTabsIfNeeded() && singleRow;
    int titleWidth = myTabs.getTitleWrapper().getPreferredSize().width;

    data.titleRect.setBounds(data.toFitRec.x, data.toFitRec.y, titleWidth, Objects.requireNonNull(myTabs.getHeaderFitSize()).height);
    data.entryPointRect.setBounds(data.toFitRec.x + data.toFitRec.width - myTabs.getEntryPointPreferredSize().width - myTabs.getActionsInsets().right,
                                  data.toFitRec.y,
                                  myTabs.getEntryPointPreferredSize().width,
                                  myTabs.getHeaderFitSize().height);
    data.moreRect.setBounds(data.toFitRec.x + data.toFitRec.width - myTabs.getEntryPointPreferredSize().width - myTabs.getActionsInsets().right,
                            data.toFitRec.y, 0, myTabs.getHeaderFitSize().height);
    calculateLengths(data);

    int eachX = data.titleRect.x + data.titleRect.width;
    Insets insets = myTabs.getLayoutInsets();
    int eachY = insets.top;
    int requiredRowsPinned = 0;
    int requiredRowsUnpinned = 0;

    int maxX = data.moreRect.x - (singleRow ? myTabs.getActionsInsets().left : 0);
    if (!singleRow && showPinnedTabsSeparately && ContainerUtil.all(visibleInfos, info -> !info.isPinned())) {
      maxX += myTabs.getEntryPointPreferredSize().width;
    }

    int hGap = myTabs.getTabHGap();
    int entryPointMargin = scrollable ? 0 : myTabs.getEntryPointPreferredSize().width;
    for (TabInfo eachInfo : data.visibleInfos) {
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
        myTabs.layout(eachLabel, eachX, eachY, width, myTabs.getHeaderFitSize().height);
        data.bounds.put(eachInfo, eachLabel.getBounds());
      }
      else {
        if ((!scrollable && eachX + width + hGap > maxX - entryPointMargin && !singleRow) || (showPinnedTabsSeparately && eachLabel.isNextToLastPinned())) {
          requiredRowsUnpinned++;
          eachY += myTabs.getHeaderFitSize().height;
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

        myTabs.layout(eachLabel, eachX - getScrollOffset(), eachY, width == 1 ? 0 : width, myTabs.getHeaderFitSize().height);
        Rectangle rectangle = new Rectangle(myTabs.getHeaderFitSize());
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
    if (requiredRowsPinned > 0 && requiredRowsUnpinned > 0) data.moreRect.y += myTabs.getHeaderFitSize().height /*+ myTabs.getSeparatorWidth()*/;

    if (data.invisible.isEmpty()) {
      data.moreRect.setBounds(0, 0, 0, 0);
    }

    eachY = -1;
    TableRow eachTableRow = new TableRow(data);

    for (TabInfo info : data.visibleInfos) {
      final TabLabel tabLabel = myTabs.getTabLabel(info);
      if (eachY == -1 || eachY != Objects.requireNonNull(tabLabel).getY()) {
        if (eachY != -1) {
          eachTableRow = new TableRow(data);
        }
        eachY = tabLabel.getY();
        data.table.add(eachTableRow);
      }
      eachTableRow.add(info, tabLabel.getWidth());
    }

    doScrollToSelectedTab(data);
    clampScrollOffsetToBounds(data);
    return data;
  }

  private void calculateLengths(TablePassInfo data) {
    boolean compressible = isCompressible();
    boolean showPinnedTabsSeparately = showPinnedTabsSeparately();

    int standardLengthToFit = data.moreRect.x - (data.titleRect.x + data.titleRect.width) - myTabs.getActionsInsets().left;
    if (compressible || showPinnedTabsSeparately) {
      if (showPinnedTabsSeparately) {
        List<TabInfo> pinned = ContainerUtil.filter(data.visibleInfos, info -> info.isPinned());
        calculateCompressibleLengths(pinned, data, standardLengthToFit);
        List<TabInfo> unpinned = ContainerUtil.filter(data.visibleInfos, info -> !info.isPinned());
        if (compressible) {
          Insets insets = myTabs.getActionsInsets();
          calculateCompressibleLengths(unpinned, data, pinned.isEmpty()
                                                       ? standardLengthToFit
                                                       : standardLengthToFit + data.titleRect.width + myTabs.getEntryPointPreferredSize().width + insets.left + insets.right);
        }
        else {
          calculateRawLengths(unpinned, data);
          if (getTotalLength(unpinned, data) > standardLengthToFit) {
            int moreWidth = getMoreRectAxisSize();
            int entryPointsWidth = pinned.isEmpty() ? myTabs.getEntryPointPreferredSize().width : 0;
            data.moreRect.setBounds(data.toFitRec.x + data.toFitRec.width - moreWidth - entryPointsWidth - myTabs.getActionsInsets().right,
                                    myTabs.getLayoutInsets().top, moreWidth, myTabs.getHeaderFitSize().height);
            calculateRawLengths(unpinned, data);
          }
        }
      }
      else {
        calculateCompressibleLengths(data.visibleInfos, data, standardLengthToFit);
      }
    }
    else {//both scrollable and multi-row
      calculateRawLengths(data.visibleInfos, data);
      if (getTotalLength(data.visibleInfos, data) > standardLengthToFit) {
        int moreWidth = getMoreRectAxisSize();
        data.moreRect.setBounds(data.toFitRec.x + data.toFitRec.width - moreWidth, data.toFitRec.y, moreWidth, myTabs.getHeaderFitSize().height);
        calculateRawLengths(data.visibleInfos, data);
      }
    }
  }

  private int getMoreRectAxisSize() {
    return myTabs.isSingleRow() ? myTabs.getMoreToolbarPreferredSize().width : 0;
  }

  private static int getTotalLength(@NotNull List<TabInfo> list, @NotNull TablePassInfo data) {
    int total = 0;
    for (TabInfo info : list) {
      total += data.lengths.get(info);
    }
    return total;
  }

  private boolean isCompressible() {
    return myTabs.isSingleRow() && !UISettings.getInstance().getHideTabsIfNeeded() && myTabs.supportCompression();
  }

  private void calculateCompressibleLengths(List<TabInfo> list, TablePassInfo data, int toFitLength) {
    if (list.isEmpty()) return;
    int spentLength = 0;
    int lengthEstimation = 0;

    for (TabInfo tabInfo : list) {
      lengthEstimation += Math.max(getMinTabWidth(), myTabs.getTabLabel(tabInfo).getPreferredSize().width);
    }

    final int extraWidth = toFitLength - lengthEstimation;

    for (Iterator<TabInfo> iterator = list.iterator(); iterator.hasNext(); ) {
      TabInfo tabInfo = iterator.next();
      final TabLabel label = myTabs.getTabLabel(tabInfo);

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
    for (TabInfo info : list) {
      TabLabel eachLabel = myTabs.getTabLabel(info);
      Dimension size =
        eachLabel.isPinned() && showPinnedTabsSeparately() ? eachLabel.getNotStrictPreferredSize() : eachLabel.getPreferredSize();
      data.lengths.put(info, Math.max(getMinTabWidth(), size.width + myTabs.getTabHGap()));
    }
  }

  public LayoutPassInfo layoutTable(List<TabInfo> visibleInfos) {
    myTabs.resetLayout(true);
    Rectangle unitedTabArea = null;
    TablePassInfo data = computeLayoutTable(visibleInfos);

    Rectangle rect = new Rectangle(data.moreRect);
    rect.y += myTabs.getBorderThickness();
    myTabs.getMoreToolbar().getComponent().setBounds(rect);

    ActionToolbar entryPointToolbar = myTabs.entryPointToolbar;
    if (entryPointToolbar != null) {
      entryPointToolbar.getComponent().setBounds(data.entryPointRect);
    }
    myTabs.getTitleWrapper().setBounds(data.titleRect);

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
      final JBTabsImpl.Toolbar selectedToolbar = myTabs.getInfoToToolbar().get(myTabs.getSelectedInfo());

      final int componentY = (unitedTabArea != null ? unitedTabArea.y + unitedTabArea.height : eachY) + (myTabs.isEditorTabs() ? 0 : 2) -
                             myTabs.getLayoutInsets().top;
      if (!myTabs.getHorizontalSide() && selectedToolbar != null && !selectedToolbar.isEmpty()) {
        final int toolbarWidth = selectedToolbar.getPreferredSize().width;
        final int vSeparatorWidth = toolbarWidth > 0 ? myTabs.separatorWidth : 0;
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
    lastTableLayout = data;
    return data;
  }

  @Override
  public boolean isTabHidden(@NotNull TabInfo info) {
    TabLabel label = myTabs.getTabLabel(info);
    Rectangle bounds = label.getBounds();
    int deadzone = JBUI.scale(DEADZONE_FOR_DECLARE_TAB_HIDDEN);
    return bounds.x < -deadzone || bounds.width < label.getPreferredSize().width - deadzone;
  }

  @Override
  public boolean isDragOut(@NotNull TabLabel tabLabel, int deltaX, int deltaY) {
    if (lastTableLayout == null) {
      return super.isDragOut(tabLabel, deltaX, deltaY);
    }

    Rectangle area = new Rectangle(lastTableLayout.toFitRec.width, tabLabel.getBounds().height);
    for (int i = 0; i < lastTableLayout.visibleInfos.size(); i++) {
      area = area.union(myTabs.getTabLabel(lastTableLayout.visibleInfos.get(i)).getBounds());
    }
    return Math.abs(deltaY) > area.height * getDragOutMultiplier();
  }

  @Override
  public int getDropIndexFor(@NotNull Point point) {
    if (lastTableLayout == null) return -1;
    int result = -1;

    Component c = myTabs.getComponentAt(point);
    Set<TabInfo> lastInRow = new HashSet<>();
    for (int i = 0; i < lastTableLayout.table.size(); i++) {
      List<TabInfo> columns = lastTableLayout.table.get(i).myColumns;
      if (!columns.isEmpty()) {
        lastInRow.add(columns.get(columns.size() - 1));
      }
    }

    if (c instanceof JBTabsImpl) {
      for (int i = 0; i < lastTableLayout.visibleInfos.size() - 1; i++) {
        TabInfo firstInfo = lastTableLayout.visibleInfos.get(i);
        TabInfo secondInfo = lastTableLayout.visibleInfos.get(i + 1);
        TabLabel first = myTabs.getTabLabel(firstInfo);
        TabLabel second = myTabs.getTabLabel(secondInfo);

        Rectangle firstBounds = Objects.requireNonNull(first).getBounds();
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
      int index = lastTableLayout.visibleInfos.indexOf(info);
      boolean isDropTarget = myTabs.isDropTarget(info);
      if (!isDropTarget) {
        for (int i = 0; i <= index; i++) {
          if (myTabs.isDropTarget(lastTableLayout.visibleInfos.get(i))) {
            index -= 1;
            break;
          }
        }
        result = index;
      }
      else if (index < lastTableLayout.visibleInfos.size()) {
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

  @Override
  public int getScrollOffset() {
    return myScrollOffset;
  }

  @Override
  public void scroll(int units) {
    if (!myTabs.isSingleRow()) {
      myScrollOffset = 0;
      return;
    }
    myScrollOffset += units;

    clampScrollOffsetToBounds(lastTableLayout);
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

  @Override
  public boolean isWithScrollBar() {
    return myWithScrollBar;
  }

  public int getScrollUnitIncrement() {
    return 10;
  }

  private void doScrollToSelectedTab(TablePassInfo data) {
    if (myTabs.isMouseInsideTabsArea()
        || data == null
        || data.lengths.isEmpty()
        || myTabs.isHideTabs()
        || !showPinnedTabsSeparately()) {
      return;
    }

    int offset = -myScrollOffset;
    for (TabInfo info : data.visibleInfos) {
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
