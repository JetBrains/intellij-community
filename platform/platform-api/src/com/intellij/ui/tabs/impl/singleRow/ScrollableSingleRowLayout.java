// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;


public class ScrollableSingleRowLayout extends SingleRowLayout {
  public static final int DEADZONE_FOR_DECLARE_TAB_HIDDEN = 10;
  private int myScrollOffset = 0;
  private boolean myScrollSelectionInViewPending = false;

  public ScrollableSingleRowLayout(final JBTabsImpl tabs) {
    super(tabs);
  }

  @Override
  int getScrollOffset() {
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
      int max = data.requiredLength - data.toFitLength + getStrategy().getMoreRectAxisSize();
      if (getStrategy() instanceof SingleRowLayoutStrategy.Vertical) {
        max += getStrategy().getEntryPointAxisSize();
      }
      myScrollOffset = Math.max(0, Math.min(myScrollOffset, max));
    }
  }

  @Override
  public void scrollSelectionInView() {
    myScrollSelectionInViewPending = true;
  }

  @Override
  public int getScrollUnitIncrement() {
    if (myLastSingRowLayout != null) {
      final List<TabInfo> visibleInfos = myLastSingRowLayout.myVisibleInfos;
      if (visibleInfos.size() > 0) {
        final TabInfo info = visibleInfos.get(0);
        return getStrategy().getScrollUnitIncrement(myTabs.myInfo2Label.get(info));
      }
    }
    return 0;
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
          int maxLength = passInfo.toFitLength - getStrategy().getMoreRectAxisSize();
          if (getStrategy() instanceof SingleRowLayoutStrategy.Vertical) {
            maxLength -= getStrategy().getEntryPointAxisSize();
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
    if (data.requiredLength > data.toFitLength && !label.isPinned()) {
      length = getStrategy().getLengthIncrement(label.getPreferredSize());
      final int moreRectSize = getStrategy().getMoreRectAxisSize();
      if (data.position + length > data.toFitLength - moreRectSize) {
        final int clippedLength = getStrategy().drawPartialOverflowTabs()
                                  ? data.toFitLength - data.position - moreRectSize : 0;
        super.applyTabLayout(data, label, clippedLength);
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
}
