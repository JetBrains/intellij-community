/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author yole
 */
public class ScrollableSingleRowLayout extends SingleRowLayout {
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
    if (myLastSingRowLayout != null) {
      int offset = -myScrollOffset;
      for (TabInfo info : myLastSingRowLayout.myVisibleInfos) {
        final int length = getRequiredLength(info);
        if (info == myTabs.getSelectedInfo()) {
          if (offset < 0) {
            myScrollOffset+=offset;
          }
          else {
            final int maxLength = myLastSingRowLayout.toFitLength - getStrategy().getMoreRectAxisSize();
            if (offset + length > maxLength) {
              myScrollOffset+=offset + length - maxLength;
            }
          }
          break;
        }
        offset += length;
      }
      clampScrollOffsetToBounds(myLastSingRowLayout);
    }
  }

  @Override
  protected boolean checkLayoutLabels(SingleRowPassInfo data) {
    if (myScrollSelectionInViewPending) {
      return true;
    }
    return super.checkLayoutLabels(data);
  }

  private void clampScrollOffsetToBounds(SingleRowPassInfo data) {
    if (data.requiredLength < data.toFitLength) {
      myScrollOffset = 0;
    }
    else {
      myScrollOffset = Math.max(0, Math.min(myScrollOffset, data.requiredLength - data.toFitLength + getStrategy().getMoreRectAxisSize()));
    }
  }

  @Override
  public void scrollSelectionInView() {
    myScrollSelectionInViewPending = true;
  }

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
    int offset = -myScrollOffset;
    for (TabInfo info : passInfo.myVisibleInfos) {
      final int length = getRequiredLength(info);
      if (info == myTabs.getSelectedInfo()) {
        if (offset < 0) {
          scroll(offset);
        }
        else {
          final int maxLength = passInfo.toFitLength - getStrategy().getMoreRectAxisSize();
          if (offset + length > maxLength) {
            scroll(offset + length - maxLength);
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
    }
  }

  protected void layoutMoreButton(SingleRowPassInfo data) {
    if (data.requiredLength > data.toFitLength) {
      data.moreRect = getStrategy().getMoreRect(data);
    }
  }

  @Override
  protected boolean applyTabLayout(SingleRowPassInfo data, TabLabel label, int length, int deltaToFit) {
    if (data.requiredLength > data.toFitLength) {
      length = getStrategy().getLengthIncrement(label.getPreferredSize());
      final int moreRectSize = getStrategy().getMoreRectAxisSize();
      if (data.position + length > data.toFitLength - moreRectSize) {
        final int clippedLength = getStrategy().drawPartialOverflowTabs() ? data.toFitLength - data.position - moreRectSize - 4 : 0;
        super.applyTabLayout(data, label, clippedLength, deltaToFit);
        label.setAlignmentToCenter(false);
        label.setActionPanelVisible(false);
        return false;
      }
    }
    label.setActionPanelVisible(true);
    return super.applyTabLayout(data, label, length, deltaToFit);
  }

  @Override
  public boolean isTabHidden(TabInfo tabInfo) {
    final TabLabel label = myTabs.myInfo2Label.get(tabInfo);
    final Rectangle bounds = label.getBounds();
    return getStrategy().getMinPosition(bounds) < -10 || bounds.isEmpty();
  }

  @Nullable
  @Override
  protected TabLabel findLastVisibleLabel(SingleRowPassInfo data) {
    int i = data.toLayout.size()-1;
    while(i > 0) {
      final TabInfo info = data.toLayout.get(i);
      final TabLabel label = myTabs.myInfo2Label.get(info);
      if (!label.getBounds().isEmpty()) {
        return label;
      }
      i--;
    }
    return null;
  }
}
