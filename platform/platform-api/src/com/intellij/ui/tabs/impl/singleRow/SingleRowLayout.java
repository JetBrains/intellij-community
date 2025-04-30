// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.ui.tabs.impl.*;
import com.intellij.util.ObjectUtils;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public abstract class SingleRowLayout extends TabLayout {
  final JBTabsImpl tabs;
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
    this.tabs = tabs;
    myTop = new SingleRowLayoutStrategy.Top(this);
    myLeft = new SingleRowLayoutStrategy.Left(this);
    myBottom = new SingleRowLayoutStrategy.Bottom(this);
    myRight = new SingleRowLayoutStrategy.Right(this);
  }

  SingleRowLayoutStrategy getStrategy() {
    return switch (tabs.getPresentation().getTabsPosition()) {
      case top -> myTop;
      case left -> myLeft;
      case bottom -> myBottom;
      case right -> myRight;
    };
  }

  protected boolean checkLayoutLabels(SingleRowPassInfo data) {
    boolean layoutLabels = true;

    if (!tabs.getForcedRelayout$intellij_platform_ide() &&
        lastSingRowLayout != null &&
        lastSingRowLayout.contentCount == tabs.getTabCount() &&
        lastSingRowLayout.layoutSize.equals(tabs.getSize()) &&
        lastSingRowLayout.scrollOffset == getScrollOffset()) {
      for (TabInfo each : data.visibleInfos) {
        final TabLabel eachLabel = tabs.getTabLabel(each);
        if (!Objects.requireNonNull(eachLabel).isValid()) {
          layoutLabels = true;
          break;
        }
        if (tabs.getSelectedInfo() == each) {
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

    final TabInfo selected = tabs.getSelectedInfo();
    prepareLayoutPassInfo(data, selected);

    tabs.resetLayout(shouldLayoutLabels || tabs.isHideTabs());

    if (shouldLayoutLabels && !tabs.isHideTabs()) {
      recomputeToLayout(data);

      data.position = getStrategy().getStartPosition(data) - getScrollOffset();

      layoutTitle(data);

      if (ExperimentalUI.isNewUI() && tabs.getTabsPosition().isSide()) {
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
      final TabLabel firstLabel = tabs.getTabLabel(data.toLayout.get(0));
      final TabLabel lastLabel = findLastVisibleLabel(data);
      if (firstLabel != null && lastLabel != null) {
        data.tabRectangle.x = firstLabel.getBounds().x;
        data.tabRectangle.y = firstLabel.getBounds().y;
        data.tabRectangle.width = ExperimentalUI.isNewUI()
                                  ? (int)data.entryPointRect.getMaxX() + tabs.getActionsInsets().right - data.tabRectangle.x
                                  : (int)lastLabel.getBounds().getMaxX() - data.tabRectangle.x;
        data.tabRectangle.height = (int)lastLabel.getBounds().getMaxY() - data.tabRectangle.y;
      }
    }

    lastSingRowLayout = data;
    return data;
  }

  protected @Nullable TabLabel findLastVisibleLabel(SingleRowPassInfo data) {
    return tabs.getTabLabel(data.toLayout.get(data.toLayout.size() - 1));
  }

  protected void prepareLayoutPassInfo(SingleRowPassInfo data, TabInfo selected) {
    data.insets = tabs.getLayoutInsets();
    if (tabs.isHorizontalTabs()) {
      data.insets.left += tabs.getFirstTabOffset();
    }

    JBTabsImpl.Toolbar selectedForeToolbar = tabs.infoToForeToolbar.get(selected);
    data.hfToolbar =
      new WeakReference<>(
        selectedForeToolbar != null && tabs.getHorizontalSide() && !selectedForeToolbar.isEmpty() ? selectedForeToolbar : null);

    final JBTabsImpl.Toolbar selectedToolbar = tabs.getInfoToToolbar().get(selected);
    data.hToolbar =
      new WeakReference<>(selectedToolbar != null && tabs.getHorizontalSide() && !selectedToolbar.isEmpty() ? selectedToolbar : null);
    data.vToolbar =
      new WeakReference<>(selectedToolbar != null && !tabs.getHorizontalSide() && !selectedToolbar.isEmpty() ? selectedToolbar : null);
    data.toFitLength = getStrategy().getToFitLength(data);
  }

  protected void layoutTitle(SingleRowPassInfo data) {
    data.titleRect = getStrategy().getTitleRect(data);
    data.position += tabs.isHorizontalTabs() ? data.titleRect.width : data.titleRect.height;
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
      final TabLabel label = tabs.getTabLabel(eachInfo);
      if (layoutStopped) {
        Rectangle rec = getStrategy().getLayoutRect(data, 0, 0);
        tabs.layout(Objects.requireNonNull(label), rec);
        continue;
      }

      final Dimension eachSize = Objects.requireNonNull(label).getPreferredSize();

      int length = getStrategy().getLengthIncrement(eachSize);
      boolean continueLayout = applyTabLayout(data, label, length);

      data.position = getStrategy().getMaxPosition(label.getBounds());
      data.position += tabs.getTabHGap();

      if (!continueLayout) {
        layoutStopped = true;
      }
    }

    for (TabInfo eachInfo : data.toDrop) {
      JBTabsImpl.Companion.resetLayout(tabs.getTabLabel(eachInfo));
    }
  }

  protected boolean applyTabLayout(SingleRowPassInfo data, TabLabel label, int length) {
    TabLabel.TabLabelLayout layout = ObjectUtils.tryCast(label.getLayout(), TabLabel.TabLabelLayout.class);
    if (layout != null) {
      layout.setRightAlignment(false);
    }
    final Rectangle rec = getStrategy().getLayoutRect(data, data.position, length);
    if (data.hfToolbar.get() != null) {
      int startPosition = getStrategy().getStartPosition(data);
      if (rec.x < startPosition) {
        rec.width -= startPosition - rec.x;
        rec.x = startPosition;
        if (layout != null) {
          layout.setRightAlignment(true);
        }
      }
    }
    tabs.layout(label, rec);

    label.setAlignmentToCenter(tabs.isEditorTabs() && getStrategy().isToCenterTextWhenStretched());
    return true;
  }


  protected abstract void recomputeToLayout(final SingleRowPassInfo data);

  protected void calculateRequiredLength(SingleRowPassInfo data) {
    data.requiredLength += tabs.isHorizontalTabs() ? data.insets.left + data.insets.right
                                                   : data.insets.top + data.insets.bottom;
    for (TabInfo eachInfo : data.visibleInfos) {
      data.requiredLength += getRequiredLength(eachInfo);
      data.toLayout.add(eachInfo);
    }
    data.requiredLength += getStrategy().getAdditionalLength(data);
  }

  protected int getRequiredLength(TabInfo eachInfo) {
    TabLabel label = tabs.getTabLabel(eachInfo);
    return getStrategy().getLengthIncrement(label != null ? label.getPreferredSize() : new Dimension())
                                      + (tabs.isEditorTabs() ? tabs.getTabHGap() : 0);
  }


  @Override
  public boolean isTabHidden(@NotNull TabInfo info) {
    return lastSingRowLayout != null && lastSingRowLayout.toDrop.contains(info);
  }

  @Override
  public int getDropIndexFor(@NotNull Point point) {
    if (lastSingRowLayout == null) return -1;

    int result = -1;

    var adjustedPoint = getStrategy().adjustDropPoint(point);
    Component c = tabs.getComponentAt(adjustedPoint);

    if (c instanceof JBTabsImpl) {
      for (int i = 0; i < lastSingRowLayout.visibleInfos.size() - 1; i++) {
        TabLabel first = tabs.getTabLabel(lastSingRowLayout.visibleInfos.get(i));
        TabLabel second = tabs.getTabLabel(lastSingRowLayout.visibleInfos.get(i + 1));

        Rectangle firstBounds = Objects.requireNonNull(first).getBounds();
        Rectangle secondBounds = Objects.requireNonNull(second).getBounds();

        final boolean between;

        boolean horizontal = getStrategy() instanceof SingleRowLayoutStrategy.Horizontal;
        if (horizontal) {
          between = firstBounds.getMaxX() < adjustedPoint.x
                    && secondBounds.getX() > adjustedPoint.x
                    && firstBounds.y < adjustedPoint.y
                    && secondBounds.getMaxY() > adjustedPoint.y;
        } else {
          between = firstBounds.getMaxY() < adjustedPoint.y
                    && secondBounds.getY() > adjustedPoint.y
                    && firstBounds.x < adjustedPoint.x
                    && secondBounds.getMaxX() > adjustedPoint.x;
        }

        if (between) {
          c = first;
          break;
        }
      }

    }

    if (c instanceof TabLabel) {
      TabInfo info = ((TabLabel)c).getInfo();
      int index = lastSingRowLayout.visibleInfos.indexOf(info);
      boolean isDropTarget = tabs.isDropTarget(info);
      if (!isDropTarget) {
        for (int i = 0; i <= index; i++) {
          if (tabs.isDropTarget(lastSingRowLayout.visibleInfos.get(i))) {
            index -= 1;
            break;
          }
        }
        result = index;
      } else if (index < lastSingRowLayout.visibleInfos.size()) {
        result = index;
      }
    }

    return result;
  }

  @Override
  @MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT, -1})
  public int getDropSideFor(@NotNull Point point) {
    return TabsUtil.getDropSideFor(point, tabs);
  }
}
