/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.List;

public class SingleRowLayout extends TabLayout {

  final JBTabsImpl myTabs;
  public SingleRowPassInfo myLastSingRowLayout;

  private final SingleRowLayoutStrategy myTop;
  private final SingleRowLayoutStrategy myLeft;
  private final SingleRowLayoutStrategy myBottom;
  private final SingleRowLayoutStrategy myRight;

  public final MoreTabsIcon myMoreIcon = new MoreTabsIcon() {
    @Nullable
    protected Rectangle getIconRec() {
      return myLastSingRowLayout != null ? myLastSingRowLayout.moreRect : null;
    }

    @Override
    protected int getIconY(Rectangle iconRec) {
      final int shift;
      switch (myTabs.getTabsPosition()) {
        case bottom: shift = myTabs.getActiveTabUnderlineHeight(); break;
        case top: shift = -(myTabs.getActiveTabUnderlineHeight() / 2); break;
        default: shift = 0;
      }
      return super.getIconY(iconRec) + shift;
    }
  };
  public JPopupMenu myMorePopup;


  public final GhostComponent myLeftGhost = new GhostComponent(RowDropPolicy.first, RowDropPolicy.first);
  public final GhostComponent myRightGhost = new GhostComponent(RowDropPolicy.last, RowDropPolicy.first);

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

  @Override
  public boolean isDragOut(TabLabel tabLabel, int deltaX, int deltaY) {
    return getStrategy().isDragOut(tabLabel, deltaX, deltaY);
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

  protected boolean checkLayoutLabels(SingleRowPassInfo data) {
    boolean layoutLabels = true;

    if (!myTabs.myForcedRelayout &&
        myLastSingRowLayout != null &&
        myLastSingRowLayout.contentCount == myTabs.getTabCount() &&
        myLastSingRowLayout.layoutSize.equals(myTabs.getSize()) &&
        myLastSingRowLayout.scrollOffset == getScrollOffset()) {
      for (TabInfo each : data.myVisibleInfos) {
        final TabLabel eachLabel = myTabs.myInfo2Label.get(each);
        if (!eachLabel.isValid()) {
          layoutLabels = true;
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

  int getScrollOffset() {
    return 0;
  }

  public void scroll(int units) {
  }

  public int getScrollUnitIncrement() {
    return 0;
  }

  public void scrollSelectionInView() {
  }

  public LayoutPassInfo layoutSingleRow(List<TabInfo> visibleInfos)  {
    SingleRowPassInfo data = new SingleRowPassInfo(this, visibleInfos);

    final boolean layoutLabels = checkLayoutLabels(data);
    if (!layoutLabels) {
      data = myLastSingRowLayout;
    }

    final TabInfo selected = myTabs.getSelectedInfo();
    prepareLayoutPassInfo(data, selected);

    myTabs.resetLayout(layoutLabels || myTabs.isHideTabs());

    if (layoutLabels && !myTabs.isHideTabs()) {
      data.position = getStrategy().getStartPosition(data) - getScrollOffset();

      recomputeToLayout(data);

      layoutLabelsAndGhosts(data);

      layoutMoreButton(data);
    }

    if (selected != null) {
      data.comp = new WeakReference<>(selected.getComponent());
      getStrategy().layoutComp(data);
    }

    updateMoreIconVisibility(data);

    data.tabRectangle = new Rectangle();

    if (data.toLayout.size() > 0) {
      final TabLabel firstLabel = myTabs.myInfo2Label.get(data.toLayout.get(0));
      final TabLabel lastLabel = findLastVisibleLabel(data);
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

  @Nullable
  protected TabLabel findLastVisibleLabel(SingleRowPassInfo data) {
    return myTabs.myInfo2Label.get(data.toLayout.get(data.toLayout.size() - 1));
  }

  protected void prepareLayoutPassInfo(SingleRowPassInfo data, TabInfo selected) {
    data.insets = myTabs.getLayoutInsets();
    if (myTabs.isHorizontalTabs()) {
      data.insets.left += myTabs.getFirstTabOffset();
    }

    final JBTabsImpl.Toolbar selectedToolbar = myTabs.myInfo2Toolbar.get(selected);
    data.hToolbar =
      new WeakReference<>(selectedToolbar != null && myTabs.myHorizontalSide && !selectedToolbar.isEmpty() ? selectedToolbar : null);
    data.vToolbar =
      new WeakReference<>(selectedToolbar != null && !myTabs.myHorizontalSide && !selectedToolbar.isEmpty() ?  selectedToolbar : null);
    data.toFitLength = getStrategy().getToFitLength(data);

    if (myTabs.isGhostsAlwaysVisible()) {
      data.toFitLength -= myTabs.getGhostTabLength() * 2 + (myTabs.getInterTabSpaceLength() * 2);
    }
  }

  protected void updateMoreIconVisibility(SingleRowPassInfo data) {
    int counter = (int)data.myVisibleInfos.stream().filter(this::isTabHidden).count();
    myMoreIcon.updateCounter(counter);
  }

  protected void layoutMoreButton(SingleRowPassInfo data) {
    if (data.toDrop.size() > 0) {
      data.moreRect = getStrategy().getMoreRect(data);
    }
  }

  protected void layoutLabelsAndGhosts(final SingleRowPassInfo data) {
    if (data.firstGhostVisible || myTabs.isGhostsAlwaysVisible()) {
      data.firstGhost = getStrategy().getLayoutRect(data, data.position, myTabs.getGhostTabLength());
      myTabs.layout(myLeftGhost, data.firstGhost);
      data.position += getStrategy().getLengthIncrement(data.firstGhost.getSize()) + myTabs.getInterTabSpaceLength();
    }

    int deltaToFit = 0;
    if (data.firstGhostVisible || data.lastGhostVisible) {
      if (data.requiredLength < data.toFitLength && getStrategy().canBeStretched()) {
        deltaToFit = (int)Math.floor((data.toFitLength - data.requiredLength) / (double)data.toLayout.size());
      }
    }

    int totalLength = 0;
    int positionStart = data.position;
    boolean layoutStopped = false;
    for (TabInfo eachInfo : data.toLayout) {
      final TabLabel label = myTabs.myInfo2Label.get(eachInfo);
      if (layoutStopped) {
        label.setActionPanelVisible(false);
        final Rectangle rec = getStrategy().getLayoutRect(data, 0, 0);
        myTabs.layout(label, rec);
        continue;
      }

      label.setActionPanelVisible(true);
      final Dimension eachSize = label.getPreferredSize();

      boolean isLast = data.toLayout.indexOf(eachInfo) == data.toLayout.size() - 1;

      int length;
      if (!isLast || deltaToFit == 0) {
        length = getStrategy().getLengthIncrement(eachSize) + deltaToFit;
      }
      else {
        length = data.toFitLength - totalLength;
      }
      boolean continueLayout = applyTabLayout(data, label, length, deltaToFit);

      data.position = getStrategy().getMaxPosition(label.getBounds());
      data.position += myTabs.getInterTabSpaceLength();

      totalLength = getStrategy().getMaxPosition(label.getBounds()) - positionStart + myTabs.getInterTabSpaceLength();
      if (!continueLayout) {
        layoutStopped = true;
      }
    }

    for (TabInfo eachInfo : data.toDrop) {
      JBTabsImpl.resetLayout(myTabs.myInfo2Label.get(eachInfo));
    }

    if (data.lastGhostVisible || myTabs.isGhostsAlwaysVisible()) {
      data.lastGhost = getStrategy().getLayoutRect(data, data.position, myTabs.getGhostTabLength());
      myTabs.layout(myRightGhost, data.lastGhost);
    }
  }

  protected boolean applyTabLayout(SingleRowPassInfo data, TabLabel label, int length, int deltaToFit) {
    final Rectangle rec = getStrategy().getLayoutRect(data, data.position, length);
    myTabs.layout(label, rec);

    label.setAlignmentToCenter((deltaToFit > 0 || myTabs.isEditorTabs()) && getStrategy().isToCenterTextWhenStretched());
    return true;
  }


  protected void recomputeToLayout(final SingleRowPassInfo data) {
    calculateRequiredLength(data);

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

    for (int i = 1; i < data.myVisibleInfos.size() - 1; i++) {
      final TabInfo each = data.myVisibleInfos.get(i);
      final TabInfo prev = data.myVisibleInfos.get(i - 1);
      final TabInfo next = data.myVisibleInfos.get(i + 1);

      if (data.toLayout.contains(each) && data.toDrop.contains(prev)) {
        myLeftGhost.setInfo(prev);
      }
      else if (data.toLayout.contains(each) && data.toDrop.contains(next)) {
        myRightGhost.setInfo(next);
      }
    }


  }

  protected void calculateRequiredLength(SingleRowPassInfo data) {
    for (TabInfo eachInfo : data.myVisibleInfos) {
      data.requiredLength += getRequiredLength(eachInfo);
      if (myTabs.getTabsPosition() == JBTabsPosition.left || myTabs.getTabsPosition() == JBTabsPosition.right) {
        data.requiredLength -= 1;
      }
      data.toLayout.add(eachInfo);
    }
  }

  protected int getRequiredLength(TabInfo eachInfo) {
    TabLabel label = myTabs.myInfo2Label.get(eachInfo);
    return getStrategy().getLengthIncrement(label != null ? label.getPreferredSize() : new Dimension())
                                      + (myTabs.isEditorTabs() ? myTabs.getInterTabSpaceLength() : 0);
  }


  public boolean isTabHidden(TabInfo tabInfo) {
    return myLastSingRowLayout != null && myLastSingRowLayout.toDrop.contains(tabInfo);
  }

  public class GhostComponent extends JLabel {
    private TabInfo myInfo;

    private GhostComponent(final RowDropPolicy before, final RowDropPolicy after) {
      addMouseListener(new MouseAdapter() {
        public void mousePressed(final MouseEvent e) {
          if (JBTabsImpl.isSelectionClick(e, true) && myInfo != null) {
            myRowDropPolicy = before;
            myTabs.select(myInfo, true).doWhenDone(() -> myRowDropPolicy = after);
          } else {
            MouseEvent event = SwingUtilities.convertMouseEvent(e.getComponent(), e, myTabs);
            myTabs.processMouseEvent(event);
          }
        }
      });
    }

    public void setInfo(@Nullable final TabInfo info) {
      myInfo = info;
      setToolTipText(info != null ? info.getTooltipText() : null);
    }

    public void reset() {
      JBTabsImpl.resetLayout(this);
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
      data.firstGhostVisible = !myTabs.isEditorTabs();
      if (!myTabs.isGhostsAlwaysVisible() && !myTabs.isEditorTabs()) {
        data.toFitLength -= myTabs.getGhostTabLength();
      }
    }
    else if (!data.lastGhostVisible && !isFirstSide) {
      data.lastGhostVisible = !myTabs.isEditorTabs();
      if (!myTabs.isGhostsAlwaysVisible() && !myTabs.isEditorTabs()) {
        data.toFitLength -= myTabs.getGhostTabLength();
      }
    }
  }

  @Override
  public int getDropIndexFor(Point point) {
    if (myLastSingRowLayout == null) return -1;

    int result = -1;

    Component c = myTabs.getComponentAt(point);

    if (c instanceof JBTabsImpl) {
      for (int i = 0; i < myLastSingRowLayout.myVisibleInfos.size() - 1; i++) {
        TabLabel first = myTabs.myInfo2Label.get(myLastSingRowLayout.myVisibleInfos.get(i));
        TabLabel second = myTabs.myInfo2Label.get(myLastSingRowLayout.myVisibleInfos.get(i + 1));

        Rectangle firstBounds = first.getBounds();
        Rectangle secondBounds = second.getBounds();

        final boolean between;

        boolean horizontal = getStrategy() instanceof SingleRowLayoutStrategy.Horizontal;
        if (horizontal) {
          between = firstBounds.getMaxX() < point.x
                    && secondBounds.getX() > point.x
                    && firstBounds.y < point.y
                    && secondBounds.getMaxY() > point.y;
        } else {
          between = firstBounds.getMaxY() < point.y
                    && secondBounds.getY() > point.y
                    && firstBounds.x < point.x
                    && secondBounds.getMaxX() > point.x;
        }

        if (between) {
          c = first;
          break;
        }
      }

    }

    if (c instanceof TabLabel) {
      TabInfo info = ((TabLabel)c).getInfo();
      int index = myLastSingRowLayout.myVisibleInfos.indexOf(info);
      boolean isDropTarget = myTabs.isDropTarget(info);
      if (!isDropTarget) {
        for (int i = 0; i <= index; i++) {
          if (myTabs.isDropTarget(myLastSingRowLayout.myVisibleInfos.get(i))) {
            index -= 1;
            break;
          }
        }
        result = index;
      } else if (index < myLastSingRowLayout.myVisibleInfos.size()) {
        result = index;
      }
    } else if (c instanceof GhostComponent) {
      GhostComponent ghost = (GhostComponent)c;
      TabInfo info = ghost.myInfo;
      if (info != null) {
        int index = myLastSingRowLayout.myVisibleInfos.indexOf(info);
        index += myLeftGhost == ghost ? -1 : 1;
        result =  index >= 0 && index < myLastSingRowLayout.myVisibleInfos.size() ? index : -1;
      } else {
        if (myLastSingRowLayout.myVisibleInfos.size() == 0) {
          result = 0;
        } else {
          result =  myLeftGhost == ghost ? 0 : myLastSingRowLayout.myVisibleInfos.size() - 1;
        }
      }
    }

    return result;
  }
}
