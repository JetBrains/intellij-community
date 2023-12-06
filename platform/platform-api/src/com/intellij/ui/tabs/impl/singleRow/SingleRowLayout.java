// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.ExperimentalUI;
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
  public SingleRowPassInfo lastSingRowLayout;

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
    return switch (myTabs.getPresentation().getTabsPosition()) {
      case top -> myTop;
      case left -> myLeft;
      case bottom -> myBottom;
      case right -> myRight;
    };
  }

  protected boolean checkLayoutLabels(SingleRowPassInfo data) {
    boolean layoutLabels = true;

    if (!myTabs.getForcedRelayout$intellij_platform_ide() &&
        lastSingRowLayout != null &&
        lastSingRowLayout.contentCount == myTabs.getTabCount() &&
        lastSingRowLayout.layoutSize.equals(myTabs.getSize()) &&
        lastSingRowLayout.scrollOffset == getScrollOffset()) {
      for (TabInfo each : data.myVisibleInfos) {
        final TabLabel eachLabel = myTabs.getInfoToLabel().get(each);
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

  public LayoutPassInfo layoutSingleRow(List<TabInfo> visibleInfos)  {
    SingleRowPassInfo data = new SingleRowPassInfo(this, visibleInfos);

    final boolean shouldLayoutLabels = checkLayoutLabels(data);
    if (!shouldLayoutLabels) {
      data = lastSingRowLayout;
    }

    final TabInfo selected = myTabs.getSelectedInfo();
    prepareLayoutPassInfo(data, selected);

    myTabs.resetLayout(shouldLayoutLabels || myTabs.isHideTabs());

    if (shouldLayoutLabels && !myTabs.isHideTabs()) {
      recomputeToLayout(data);

      data.position = getStrategy().getStartPosition(data) - getScrollOffset();

      layoutTitle(data);

      if (ExperimentalUI.isNewUI() && myTabs.getTabsPosition().isSide()) {
        // Layout buttons first because their position will be used to calculate label positions
        layoutEntryPointButton(data);
        layoutMoreButton(data);
        layoutLabels(data);
      }
      else {
        layoutLabels(data);
        layoutEntryPointButton(data);
        layoutMoreButton(data);
      }
    }

    if (selected != null) {
      data.component = new WeakReference<>(selected.getComponent());
      getStrategy().layoutComp(data);
    }

    data.tabRectangle = new Rectangle();

    if (!data.toLayout.isEmpty()) {
      final TabLabel firstLabel = myTabs.getInfoToLabel().get(data.toLayout.get(0));
      final TabLabel lastLabel = findLastVisibleLabel(data);
      if (firstLabel != null && lastLabel != null) {
        data.tabRectangle.x = firstLabel.getBounds().x;
        data.tabRectangle.y = firstLabel.getBounds().y;
        data.tabRectangle.width = ExperimentalUI.isNewUI()
                                  ? (int)data.entryPointRect.getMaxX() + myTabs.getActionsInsets().right - data.tabRectangle.x
                                  : (int)lastLabel.getBounds().getMaxX() - data.tabRectangle.x;
        data.tabRectangle.height = (int)lastLabel.getBounds().getMaxY() - data.tabRectangle.y;
      }
    }

    lastSingRowLayout = data;
    return data;
  }

  protected @Nullable TabLabel findLastVisibleLabel(SingleRowPassInfo data) {
    return myTabs.getInfoToLabel().get(data.toLayout.get(data.toLayout.size() - 1));
  }

  protected void prepareLayoutPassInfo(SingleRowPassInfo data, TabInfo selected) {
    data.insets = myTabs.getLayoutInsets();
    if (myTabs.isHorizontalTabs()) {
      data.insets.left += myTabs.getFirstTabOffset();
    }

    final JBTabsImpl.Toolbar selectedToolbar = myTabs.getInfoToToolbar().get(selected);
    data.hToolbar =
      new WeakReference<>(selectedToolbar != null && myTabs.getHorizontalSide() && !selectedToolbar.isEmpty() ? selectedToolbar : null);
    data.vToolbar =
      new WeakReference<>(selectedToolbar != null && !myTabs.getHorizontalSide() && !selectedToolbar.isEmpty() ? selectedToolbar : null);
    data.toFitLength = getStrategy().getToFitLength(data);
  }

  protected void layoutTitle(SingleRowPassInfo data) {
    data.titleRect = getStrategy().getTitleRect(data);
    data.position += myTabs.isHorizontalTabs() ? data.titleRect.width : data.titleRect.height;
  }

  protected void layoutMoreButton(SingleRowPassInfo data) {
    if (!data.toDrop.isEmpty()) {
      data.moreRect = getStrategy().getMoreRect(data);
    }
  }

  protected void layoutEntryPointButton(SingleRowPassInfo data) {
    data.entryPointRect = getStrategy().getEntryPointRect(data);
  }

  protected void layoutLabels(final SingleRowPassInfo data) {
    boolean layoutStopped = false;
    for (TabInfo eachInfo : data.toLayout) {
      final TabLabel label = myTabs.getInfoToLabel().get(eachInfo);
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
      JBTabsImpl.Companion.resetLayout(myTabs.getInfoToLabel().get(eachInfo));
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
    data.requiredLength += myTabs.isHorizontalTabs() ? data.insets.left + data.insets.right
                                                     : data.insets.top + data.insets.bottom;
    for (TabInfo eachInfo : data.myVisibleInfos) {
      data.requiredLength += getRequiredLength(eachInfo);
      data.toLayout.add(eachInfo);
    }
    data.requiredLength += getStrategy().getAdditionalLength();
  }

  protected int getRequiredLength(TabInfo eachInfo) {
    TabLabel label = myTabs.getInfoToLabel().get(eachInfo);
    return getStrategy().getLengthIncrement(label != null ? label.getPreferredSize() : new Dimension())
                                      + (myTabs.isEditorTabs() ? myTabs.getTabHGap() : 0);
  }


  @Override
  public boolean isTabHidden(@NotNull TabInfo info) {
    return lastSingRowLayout != null && lastSingRowLayout.toDrop.contains(info);
  }

  @Override
  public int getDropIndexFor(Point point) {
    if (lastSingRowLayout == null) return -1;

    int result = -1;

    Component c = myTabs.getComponentAt(point);

    if (c instanceof JBTabsImpl) {
      for (int i = 0; i < lastSingRowLayout.myVisibleInfos.size() - 1; i++) {
        TabLabel first = myTabs.getInfoToLabel().get(lastSingRowLayout.myVisibleInfos.get(i));
        TabLabel second = myTabs.getInfoToLabel().get(lastSingRowLayout.myVisibleInfos.get(i + 1));

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
      int index = lastSingRowLayout.myVisibleInfos.indexOf(info);
      boolean isDropTarget = myTabs.isDropTarget(info);
      if (!isDropTarget) {
        for (int i = 0; i <= index; i++) {
          if (myTabs.isDropTarget(lastSingRowLayout.myVisibleInfos.get(i))) {
            index -= 1;
            break;
          }
        }
        result = index;
      } else if (index < lastSingRowLayout.myVisibleInfos.size()) {
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
