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
      clampScrollOffsetToBounds(myLastSingRowLayout);
    }
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
  public void scrollSelectionInView(List<TabInfo> visibleInfos) {
    myScrollSelectionInViewPending = true;
  }

  private void doScrollSelectionInView(SingleRowPassInfo passInfo) {
    int offset = -myScrollOffset;
    for (TabInfo info : passInfo.myVisibleInfos) {
      final int length = getRequiredLength(info);
      if (info == myTabs.getSelectedInfo()) {
        if (offset < 0) {
          scroll(offset);
        }
        else if (offset + length > passInfo.toFitLength - getStrategy().getMoreRectAxisSize()) {
          scroll(offset + length - passInfo.toFitLength + getStrategy().getMoreRectAxisSize());
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
    if (myScrollSelectionInViewPending || !data.layoutSize.equals(myLastSingRowLayout.layoutSize)) {
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
  protected void updateMoreIconVisibility(SingleRowPassInfo data) {
    myMoreIcon.setPainted(data.requiredLength > data.toFitLength);
  }

  @Override
  protected boolean applyTabLayout(SingleRowPassInfo data, TabLabel label, int length, int deltaToFit) {
    if (data.requiredLength > data.toFitLength) {
      length = getStrategy().getLengthIncrement(label.getPreferredSize());
      final int moreRectSize = getStrategy().getMoreRectAxisSize();
      if (data.position + length > data.toFitLength - moreRectSize) {
        super.applyTabLayout(data, label, data.toFitLength - data.position - moreRectSize - 4, deltaToFit);
        label.setAlignmentToCenter(false);
        label.setActionPanelVisible(false);
        return false;
      }
    }
    label.setActionPanelVisible(true);
    return super.applyTabLayout(data, label, length, deltaToFit);
  }
}
