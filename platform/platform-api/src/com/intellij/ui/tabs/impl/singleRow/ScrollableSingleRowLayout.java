// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.TabLayout;
import org.jetbrains.annotations.Nullable;

import java.awt.*;


public class ScrollableSingleRowLayout extends SingleRowLayout {
  public static final int DEADZONE_FOR_DECLARE_TAB_HIDDEN = 10;
  private int myScrollOffset = 0;
  private boolean myScrollSelectionInViewPending = false;
  private final boolean myWithScrollBar;

  public ScrollableSingleRowLayout(final JBTabsImpl tabs) {
    this(tabs, false);
  }

  public ScrollableSingleRowLayout(final JBTabsImpl tabs, boolean isWithScrollBar) {
    super(tabs);
    myWithScrollBar = isWithScrollBar;
  }

  @Override
  public int getScrollOffset() {
    return myScrollOffset;
  }

  @Override
  public void scroll(int units) {
    myScrollOffset += units;
    clampScrollOffsetToBounds(myLastSingRowLayout);
  }

  @Override
  protected boolean checkLayoutLabels(SingleRowPassInfo data) {
    if (myScrollSelectionInViewPending) {
      return true;
    }
    return super.checkLayoutLabels(data);
  }

  private void clampScrollOffsetToBounds(@Nullable SingleRowPassInfo data) {
    if (data == null) {
      return;
    }
    if (data.requiredLength < data.toFitLength) {
      myScrollOffset = 0;
    }
    else {
      int max = data.requiredLength - data.toFitLength + getMoreRectAxisSize();
      if (!ExperimentalUI.isNewUI() && getStrategy() instanceof SingleRowLayoutStrategy.Vertical) {
        max += data.entryPointAxisSize;
      }
      myScrollOffset = Math.max(0, Math.min(myScrollOffset, max));
    }
  }

  @Override
  public void scrollSelectionInView() {
    myScrollSelectionInViewPending = true;
  }

  private void doScrollSelectionInView(SingleRowPassInfo passInfo) {
    if (myTabs.isMouseInsideTabsArea()) {
      return;
    }
    int offset = -myScrollOffset;
    for (TabInfo info : passInfo.myVisibleInfos) {
      final int length = getRequiredLength(info);
      if (info == myTabs.getSelectedInfo()) {
        if (offset < 0) {
          scroll(offset);
        }
        else {
          int maxLength = passInfo.toFitLength - getMoreRectAxisSize();
          if (!ExperimentalUI.isNewUI() && getStrategy() instanceof SingleRowLayoutStrategy.Vertical) {
            maxLength -= passInfo.entryPointAxisSize;
          }
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

  @Override
  protected void recomputeToLayout(SingleRowPassInfo data) {
    calculateRequiredLength(data);
    clampScrollOffsetToBounds(data);
    if (myScrollSelectionInViewPending || myLastSingRowLayout == null || !data.layoutSize.equals(myLastSingRowLayout.layoutSize)) {
      myScrollSelectionInViewPending = false;
      doScrollSelectionInView(data);
      clampScrollOffsetToBounds(data);
    }
  }

  @Override
  protected void layoutMoreButton(SingleRowPassInfo data) {
    if (data.requiredLength > data.toFitLength) {
      data.moreRect = getStrategy().getMoreRect(data);
    }
  }

  @Override
  protected boolean applyTabLayout(SingleRowPassInfo data, TabLabel label, int length) {
    if (data.requiredLength > data.toFitLength && !(label.isPinned() && TabLayout.showPinnedTabsSeparately())) {
      length = getStrategy().getLengthIncrement(label.getPreferredSize());
      int moreRectSize = getMoreRectAxisSize();
      if (data.entryPointAxisSize == 0) {
        Insets insets = myTabs.getActionsInsets();
        moreRectSize += insets.left + insets.right;
      }
      if (data.position + length > data.toFitLength - moreRectSize) {
        if (getStrategy().drawPartialOverflowTabs()) {
          int clippedLength = ExperimentalUI.isNewUI() && myTabs.getTabsPosition().isSide()
                              ? length : data.toFitLength - data.position - moreRectSize;
          final Rectangle rec = getStrategy().getLayoutRect(data, data.position, clippedLength);
          myTabs.layout(label, rec);
        }
        label.setAlignmentToCenter(false);
        return false;
      }
    }
    return super.applyTabLayout(data, label, length);
  }

  @Override
  public boolean isTabHidden(TabInfo tabInfo) {
    final TabLabel label = myTabs.myInfo2Label.get(tabInfo);
    final Rectangle bounds = label.getBounds();
    return getStrategy().getMinPosition(bounds) < -DEADZONE_FOR_DECLARE_TAB_HIDDEN
           || bounds.width < label.getPreferredSize().width - DEADZONE_FOR_DECLARE_TAB_HIDDEN
           || bounds.height < label.getPreferredSize().height - DEADZONE_FOR_DECLARE_TAB_HIDDEN;
  }

  @Nullable
  @Override
  protected TabLabel findLastVisibleLabel(SingleRowPassInfo data) {
    int i = data.toLayout.size() - 1;
    while (i >= 0) {
      TabInfo info = data.toLayout.get(i);
      TabLabel label = myTabs.myInfo2Label.get(info);
      if (!label.getBounds().isEmpty()) {
        return label;
      }
      i--;
    }
    return null;
  }

  private int getMoreRectAxisSize() {
    if (ExperimentalUI.isNewUI() && myTabs.getPosition().isSide()) {
      return 0;
    }
    return getStrategy().getMoreRectAxisSize();
  }

  @Override
  public boolean isWithScrollBar() {
    return myWithScrollBar;
  }
}
