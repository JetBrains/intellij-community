// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.ui.tabs.impl.*;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.List;

public abstract class SingleRowLayout extends TabLayout {

  final JBTabsImpl myTabs;
  public SingleRowPassInfo myLastSingRowLayout;

  private final SingleRowLayoutStrategy myTop;
  private final SingleRowLayoutStrategy myLeft;
  private final SingleRowLayoutStrategy myBottom;
  private final SingleRowLayoutStrategy myRight;

  @Override
  public boolean isSideComponentOnTabs() {
    return getStrategy().isSideComponentOnTabs();
  }

  @Override
  public ShapeTransform createShapeTransform(Rectangle labelRec) {
    return getStrategy().createShapeTransform(labelRec);
  }

  @Override
  public boolean isDragOut(@NotNull TabLabel tabLabel, int deltaX, int deltaY) {
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

    final boolean shouldLayoutLabels = checkLayoutLabels(data);
    if (!shouldLayoutLabels) {
      data = myLastSingRowLayout;
    }

    final TabInfo selected = myTabs.getSelectedInfo();
    prepareLayoutPassInfo(data, selected);

    myTabs.resetLayout(shouldLayoutLabels || myTabs.isHideTabs());

    if (shouldLayoutLabels && !myTabs.isHideTabs()) {
      recomputeToLayout(data);

      data.position = getStrategy().getStartPosition(data) - getScrollOffset();

      layoutTitle(data);
      data.position += myTabs.isHorizontalTabs() ? data.titleRect.width : data.titleRect.height;

      layoutLabels(data);

      layoutMoreButton(data);
    }

    if (selected != null) {
      data.comp = new WeakReference<>(selected.getComponent());
      getStrategy().layoutComp(data);
    }

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
  }

  protected void layoutTitle(SingleRowPassInfo data) {
    data.titleRect = getStrategy().getTitleRect(data);
  }

  protected void layoutMoreButton(SingleRowPassInfo data) {
    if (data.toDrop.size() > 0) {
      data.moreRect = getStrategy().getMoreRect(data);
    }
  }

  protected void layoutLabels(final SingleRowPassInfo data) {
    boolean layoutStopped = false;
    for (TabInfo eachInfo : data.toLayout) {
      final TabLabel label = myTabs.myInfo2Label.get(eachInfo);
      if (layoutStopped) {
        final Rectangle rec = getStrategy().getLayoutRect(data, 0, 0);
        myTabs.layout(label, rec);
        continue;
      }

      final Dimension eachSize = label.getPreferredSize();

      int length = getStrategy().getLengthIncrement(eachSize);
      boolean continueLayout = applyTabLayout(data, label, length);

      data.position = getStrategy().getMaxPosition(label.getBounds());
      data.position += myTabs.getTabHGap();

      if (!continueLayout) {
        layoutStopped = true;
      }
    }

    for (TabInfo eachInfo : data.toDrop) {
      JBTabsImpl.resetLayout(myTabs.myInfo2Label.get(eachInfo));
    }
  }

  protected boolean applyTabLayout(SingleRowPassInfo data, TabLabel label, int length) {
    final Rectangle rec = getStrategy().getLayoutRect(data, data.position, length);
    myTabs.layout(label, rec);

    label.setAlignmentToCenter(myTabs.isEditorTabs() && getStrategy().isToCenterTextWhenStretched());
    return true;
  }


  protected abstract void recomputeToLayout(final SingleRowPassInfo data);

  protected void calculateRequiredLength(SingleRowPassInfo data) {
    for (TabInfo eachInfo : data.myVisibleInfos) {
      data.requiredLength += getRequiredLength(eachInfo);
      if (myTabs.getTabsPosition().isSide()) {
        data.requiredLength -= 1;
      }
      data.toLayout.add(eachInfo);
    }
  }

  protected int getRequiredLength(TabInfo eachInfo) {
    TabLabel label = myTabs.myInfo2Label.get(eachInfo);
    return getStrategy().getLengthIncrement(label != null ? label.getPreferredSize() : new Dimension())
                                      + (myTabs.isEditorTabs() ? myTabs.getTabHGap() : 0);
  }


  public boolean isTabHidden(TabInfo tabInfo) {
    return myLastSingRowLayout != null && myLastSingRowLayout.toDrop.contains(tabInfo);
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
    }

    return result;
  }

  @Override
  @MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT, -1})
  public int getDropSideFor(@NotNull Point point) {
    return TabsUtil.getDropSideFor(point, myTabs);
  }
}
