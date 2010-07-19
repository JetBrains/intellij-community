/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SingleRowLayout extends TabLayout {

  JBTabsImpl myTabs;
  public SingleRowPassInfo myLastSingRowLayout;

  final SingleRowLayoutStrategy myTop;
  final SingleRowLayoutStrategy myLeft;
  final SingleRowLayoutStrategy myBottom;
  final SingleRowLayoutStrategy myRight;

  public MoreIcon myMoreIcon = new MoreIcon() {
    protected boolean isActive() {
      return myTabs.myFocused;
    }

    protected Rectangle getIconRec() {
      return myLastSingRowLayout != null ? myLastSingRowLayout.moreRect : null;
    }
  };
  public JPopupMenu myMorePopup;


  public GhostComponent myLeftGhost = new GhostComponent(RowDropPolicy.first, RowDropPolicy.first);
  public GhostComponent myRightGhost = new GhostComponent(RowDropPolicy.last, RowDropPolicy.first);

  private enum RowDropPolicy {
    first, last
  }

  private RowDropPolicy myRowDropPolicy = RowDropPolicy.first;


  @Override
  public boolean isSideComponentOnTabs() {
    return getStrategy().isSideComponentOnTabs();
  }

  @Override
  public ShapeTransform createShapeTransform(Rectangle labelRec) {
    return getStrategy().createShapeTransform(labelRec);
  }

  public SingleRowLayout(final JBTabsImpl tabs) {
    myTabs = tabs;
    myTop = new SingleRowLayoutStrategy.Top(this);
    myLeft = new SingleRowLayoutStrategy.Left(this);
    myBottom = new SingleRowLayoutStrategy.Bottom(this);
    myRight = new SingleRowLayoutStrategy.Right(this);
  }

  SingleRowLayoutStrategy getStrategy() {
    switch (myTabs.getPresentation().getTabsPosition()) {
      case top:
        return myTop;
      case left:
        return myLeft;
      case bottom:
        return myBottom;
      case right:
        return myRight;
    }

    return null;
  }

  private boolean checkLayoutLabels() {
  boolean layoutLabels = true;

    if (!myTabs.myForcedRelayout &&
        myLastSingRowLayout != null &&
        myLastSingRowLayout.contentCount == myTabs.getTabCount() &&
        myLastSingRowLayout.laayoutSize.equals(myTabs.getSize())) {
      for (TabInfo each : myTabs.myVisibleInfos) {
        final TabLabel eachLabel = myTabs.myInfo2Label.get(each);
        if (!eachLabel.isValid()) {
          break;
        }
        if (myTabs.getSelectedInfo() == each) {
          if (eachLabel.getBounds().width != 0) {
            layoutLabels = false;
          }
        }
      }
    }

    return layoutLabels;
  }

  public LayoutPassInfo layoutSingleRow() {
    SingleRowPassInfo data = new SingleRowPassInfo(this);
    final TabInfo selected = myTabs.getSelectedInfo();
    final JBTabsImpl.Toolbar selectedToolbar = myTabs.myInfo2Toolbar.get(selected);

    final boolean layoutLabels = checkLayoutLabels();
    if (!layoutLabels) {
      data = myLastSingRowLayout;
    }


    data.insets = myTabs.getLayoutInsets();

    data.hToolbar = selectedToolbar != null && myTabs.myHorizontalSide && !selectedToolbar.isEmpty() ? selectedToolbar : null;
    data.vToolbar = selectedToolbar != null && !myTabs.myHorizontalSide && !selectedToolbar.isEmpty() ? selectedToolbar : null;

    myTabs.resetLayout(layoutLabels || myTabs.isHideTabs());


    if (layoutLabels && !myTabs.isHideTabs()) {
      data.position = getStrategy().getStartPosition(data);

      recomputeToLayout(data);

      layoutLabelsAndGhosts(data);

      if (data.toDrop.size() > 0) {
        data.moreRect = getStrategy().getMoreRect(data);
      }
    }

    if (selected != null) {
      data.comp = selected.getComponent();
      getStrategy().layoutComp(data);
    }

    if (data.toLayout.size() > 0 && myTabs.myVisibleInfos.size() > 0) {
      final int left = myTabs.myVisibleInfos.indexOf(data.toLayout.get(0));
      final int right = myTabs.myVisibleInfos.indexOf(data.toLayout.get(data.toLayout.size() - 1));
      myMoreIcon.setPaintedIcons(left > 0, right < myTabs.myVisibleInfos.size() - 1);
    }
    else {
      myMoreIcon.setPaintedIcons(false, false);
    }

    data.tabRectangle = new Rectangle();

    if (data.toLayout.size() > 0) {
      final TabLabel firstLabel = myTabs.myInfo2Label.get(data.toLayout.get(0));
      final TabLabel lastLabel = myTabs.myInfo2Label.get(data.toLayout.get(data.toLayout.size() - 1));
      if (firstLabel != null && lastLabel != null) {
        data.tabRectangle.x = firstLabel.getBounds().x;
        data.tabRectangle.y = firstLabel.getBounds().y;
        data.tabRectangle.width = (int)lastLabel.getBounds().getMaxX() - data.tabRectangle.x;
        data.tabRectangle.height = (int)lastLabel.getBounds().getMaxY() - data.tabRectangle.y;
      }
    }

    myLastSingRowLayout = data;
    return data;
  }

  private void layoutLabelsAndGhosts(final SingleRowPassInfo data) {
    final int fixedPosition = getStrategy().getFixedPosition(data);
    boolean reachedBounds = false;


    if (data.firstGhostVisible || myTabs.isGhostsAlwaysVisible()) {
      data.firstGhost = getStrategy().getLayoutRec(data.position, fixedPosition, myTabs.getGhostTabLength(), getStrategy().getFixedFitLength(data));
      myTabs.layout(myLeftGhost, data.firstGhost);
      data.position += getStrategy().getLengthIncrement(data.firstGhost.getSize());
    }

    int deltaToFit = 0;
    if (data.firstGhostVisible || data.lastGhostVisible) {
      if (data.requiredLength < data.toFitLength && getStrategy().canBeStretched()) {
        deltaToFit = (int)Math.floor((data.toFitLength - data.requiredLength) / (double)data.toLayout.size());
      }
    }

    int totalLength = 0;
    int positionStart = data.position;
    for (TabInfo eachInfo : data.toLayout) {
      final TabLabel label = myTabs.myInfo2Label.get(eachInfo);
      final Dimension eachSize = label.getPreferredSize();

      boolean isLast = data.toLayout.indexOf(eachInfo) == data.toLayout.size() - 1;

      if (!isLast || deltaToFit == 0) {
        Rectangle rec = getStrategy().getLayoutRec(data.position, fixedPosition, getStrategy().getLengthIncrement(eachSize) + deltaToFit, getStrategy().getFixedFitLength(data));
        myTabs.layout(label, rec);
      }
      else {
        int length = data.toFitLength - totalLength;
        final Rectangle rec = getStrategy().getLayoutRec(data.position, fixedPosition, length, getStrategy().getFixedFitLength(data));
        myTabs.layout(label, rec);
      }

      label.setAligmentToCenter(deltaToFit > 0 && getStrategy().isToCenterTextWhenStretched());

      data.position = getStrategy().getMaxPosition(label.getBounds());
      data.position++;

      totalLength = getStrategy().getMaxPosition(label.getBounds()) - positionStart;
    }

    for (TabInfo eachInfo : data.toDrop) {
      myTabs.resetLayout(myTabs.myInfo2Label.get(eachInfo));
    }

    if (data.lastGhostVisible || myTabs.isGhostsAlwaysVisible()) {
      data.lastGhost = getStrategy().getLayoutRec(data.position, fixedPosition, myTabs.getGhostTabLength(), getStrategy().getFixedFitLength(data));
      myTabs.layout(myRightGhost, data.lastGhost);
    }
  }


  private void recomputeToLayout(final SingleRowPassInfo data) {
    data.toFitLength = getStrategy().getToFitLength(data);

    if (myTabs.isGhostsAlwaysVisible()) {
      data.toFitLength -= myTabs.getGhostTabLength() * 2;
    }
                                
    for (TabInfo eachInfo : myTabs.myVisibleInfos) {
      data.requiredLength += getStrategy().getLengthIncrement(myTabs.myInfo2Label.get(eachInfo).getPreferredSize());
      data.toLayout.add(eachInfo);
    }

    while (true) {
      if (data.requiredLength <= data.toFitLength - data.position) break;
      if (data.toLayout.size() == 0) break;

      final TabInfo first = data.toLayout.get(0);
      final TabInfo last = data.toLayout.get(data.toLayout.size() - 1);


      if (myRowDropPolicy == RowDropPolicy.first) {
        if (first != myTabs.getSelectedInfo()) {
          processDrop(data, first, true);
        }
        else if (last != myTabs.getSelectedInfo()) {
          processDrop(data, last, false);
        }
        else {
          break;
        }
      }
      else {
        if (last != myTabs.getSelectedInfo()) {
          processDrop(data, last, false);
        }
        else if (first != myTabs.getSelectedInfo()) {
          processDrop(data, first, true);
        }
        else {
          break;
        }
      }
    }

    for (int i = 1; i < myTabs.myVisibleInfos.size() - 1; i++) {
      final TabInfo each = myTabs.myVisibleInfos.get(i);
      final TabInfo prev = myTabs.myVisibleInfos.get(i - 1);
      final TabInfo next = myTabs.myVisibleInfos.get(i + 1);

      if (data.toLayout.contains(each) && data.toDrop.contains(prev)) {
        myLeftGhost.setInfo(prev);
      }
      else if (data.toLayout.contains(each) && data.toDrop.contains(next)) {
        myRightGhost.setInfo(next);
      }
    }


  }


  public class GhostComponent extends JLabel {
    private TabInfo myInfo;

    private GhostComponent(final RowDropPolicy before, final RowDropPolicy after) {
      addMouseListener(new MouseAdapter() {
        public void mousePressed(final MouseEvent e) {
          if (myTabs.isSelectionClick(e, true) && myInfo != null) {
            myRowDropPolicy = before;
            myTabs.select(myInfo, true).doWhenDone(new Runnable() {
              public void run() {
                myRowDropPolicy = after;
              }
            });
          }
        }
      });
    }

    public void setInfo(final TabInfo info) {
      myInfo = info;
      setToolTipText(info != null ? info.getTooltipText() : null);
    }

    public void reset() {
      myTabs.resetLayout(this);
      setInfo(null);
    }
  }

  private void processDrop(final SingleRowPassInfo data, final TabInfo info, boolean isFirstSide) {
    data.requiredLength -= getStrategy().getLengthIncrement(myTabs.myInfo2Label.get(info).getPreferredSize());
    data.toDrop.add(info);
    data.toLayout.remove(info);
    if (data.toDrop.size() == 1) {
      data.toFitLength -= data.moreRectAxisSize;
    }

    if (!data.firstGhostVisible && isFirstSide) {
      data.firstGhostVisible = true;
      if (!myTabs.isGhostsAlwaysVisible()) {
        data.toFitLength -= myTabs.getGhostTabLength();
      }
    }
    else if (!data.lastGhostVisible && !isFirstSide) {
      data.lastGhostVisible = true;
      if (!myTabs.isGhostsAlwaysVisible()) {
        data.toFitLength -= myTabs.getGhostTabLength();
      }
    }
  }
}
