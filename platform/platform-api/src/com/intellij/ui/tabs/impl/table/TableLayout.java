/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui.tabs.impl.table;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.TabLayout;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TableLayout extends TabLayout {

  private final JBTabsImpl myTabs;

  public TablePassInfo myLastTableLayout;

  public TableLayout(final JBTabsImpl tabs) {
    myTabs = tabs;
  }

  private TablePassInfo computeLayoutTable(List<TabInfo> visibleInfos) {
    final TablePassInfo data = new TablePassInfo(myTabs, visibleInfos);

    final Insets insets = myTabs.getLayoutInsets();
    data.toFitRec =
        new Rectangle(insets.left, insets.top, myTabs.getWidth() - insets.left - insets.right, myTabs.getHeight() - insets.top - insets.bottom);
    int eachRow = 0, eachX = data.toFitRec.x;
    TableRow eachTableRow = new TableRow(data);
    data.table.add(eachTableRow);


    data.requiredRows = 1;
    for (TabInfo eachInfo : data.myVisibleInfos) {
      final TabLabel eachLabel = myTabs.myInfo2Label.get(eachInfo);
      final Dimension size = eachLabel.getPreferredSize();
      if (eachX + size.width >= data.toFitRec.getMaxX()) {
        data.requiredRows++;
        eachX = data.toFitRec.x;
      }
      myTabs.layout(eachLabel, eachX, 0, size.width, 1);
      eachX += size.width + myTabs.getInterTabSpaceLength();
      data.requiredWidth += size.width + myTabs.getInterTabSpaceLength();
    }

    int selectedRow = -1;
    eachX = data.toFitRec.x;
    data.rowToFitMaxX = (int)data.toFitRec.getMaxX();

    if (data.requiredRows > 1) {
      final int rowFit = insets.left + data.requiredWidth / data.requiredRows;
      for (TabInfo eachInfo : data.myVisibleInfos) {
        final TabLabel eachLabel = myTabs.myInfo2Label.get(eachInfo);
        final Rectangle eachBounds = eachLabel.getBounds();
        if (eachBounds.contains(rowFit, 0)) {
          data.rowToFitMaxX = (int)eachLabel.getBounds().getMaxX();
          break;
        }
      }
    }

    for (TabInfo eachInfo : data.myVisibleInfos) {
      final TabLabel eachLabel = myTabs.myInfo2Label.get(eachInfo);
      final Dimension size = eachLabel.getPreferredSize();
      if (eachX + size.width <= data.rowToFitMaxX) {
        eachTableRow.add(eachInfo);
        if (myTabs.getSelectedInfo() == eachInfo) {
          selectedRow = eachRow;
        }
        eachX += size.width + myTabs.getInterTabSpaceLength();
      }
      else {
        eachTableRow = new TableRow(data);
        data.table.add(eachTableRow);
        eachRow++;
        eachX = insets.left + size.width;
        eachTableRow.add(eachInfo);
        if (myTabs.getSelectedInfo() == eachInfo) {
          selectedRow = eachRow;
        }
      }
    }

    List<TableRow> toMove = new ArrayList<>();
    for (int i = selectedRow + 1; i < data.table.size(); i++) {
      toMove.add(data.table.get(i));
    }

    //for (TableRow eachMove : toMove) {
    //  data.table.remove(eachMove);
    //  data.table.add(0, eachMove);
    //}


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
    final TablePassInfo data = computeLayoutTable(visibleInfos);
    final Insets insets = myTabs.getLayoutInsets();
    int eachY = insets.top;
    int eachX;
    int row = 0;
    final int tabUnderlineFix = myTabs.isEditorTabs() ? myTabs.getActiveTabUnderlineHeight() : 0;
    
    for (TableRow eachRow : data.table) {
      eachX = insets.left;

      int deltaToFit = 0;
      boolean toAjust = false;
      if (eachRow.width < data.toFitRec.width && data.table.size() > 1) {
        deltaToFit = (int)Math.floor((double)(data.toFitRec.width - eachRow.width) / (double)eachRow.myColumns.size());
        toAjust = true;
      }

      for (int i = 0; i < eachRow.myColumns.size(); i++) {
        TabInfo tabInfo = eachRow.myColumns.get(i);
        final TabLabel label = myTabs.myInfo2Label.get(tabInfo);

        label.putClientProperty(JBTabsImpl.STRETCHED_BY_WIDTH, Boolean.valueOf(toAjust));
        
        int width;
        if (i < eachRow.myColumns.size() - 1 || !toAjust) {
          width = label.getPreferredSize().width + deltaToFit;
        }
        else {
          width = data.toFitRec.width + insets.left - eachX;
        }

        myTabs.layout(label, eachX, eachY, width, row < data.table.size() - 1 ? myTabs.myHeaderFitSize.height - tabUnderlineFix :  myTabs.myHeaderFitSize.height);
        label.setAlignmentToCenter(deltaToFit > 0);

        boolean lastCell = i == eachRow.myColumns.size() - 1;
        eachX += width + (lastCell ? 0 : myTabs.getInterTabSpaceLength());
      }
      eachY += myTabs.myHeaderFitSize.height - 1 + myTabs.getInterTabSpaceLength() - (row < data.table.size() - 1 ? tabUnderlineFix : 0);
      
      row++;
    }

    if (myTabs.getSelectedInfo() != null) {
      final JBTabsImpl.Toolbar selectedToolbar = myTabs.myInfo2Toolbar.get(myTabs.getSelectedInfo());

      final int componentY = eachY + (myTabs.isEditorTabs() ? 0 : 2) - myTabs.getLayoutInsets().top;
      if (!myTabs.myHorizontalSide && selectedToolbar != null && !selectedToolbar.isEmpty()) {
        final int toolbarWidth = selectedToolbar.getPreferredSize().width;
        final int vSeparatorWidth = toolbarWidth > 0 ? 1 : 0;
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
}
