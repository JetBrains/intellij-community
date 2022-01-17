// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;

import java.util.Iterator;

public class CompressibleSingleRowLayout extends SingleRowLayout {
  public CompressibleSingleRowLayout(JBTabsImpl tabs) {
    super(tabs);
  }

  @Override
  protected void recomputeToLayout(SingleRowPassInfo data) {
    calculateRequiredLength(data);
  }

  @Override
  protected void layoutLabels(SingleRowPassInfo data) {
    if (myTabs.getPresentation().getTabsPosition() != JBTabsPosition.top
        && myTabs.getPresentation().getTabsPosition() != JBTabsPosition.bottom) {
      super.layoutLabels(data);
      return;
    }

    int spentLength = 0;
    int lengthEstimation = 0;

    for (TabInfo tabInfo : data.toLayout) {
      lengthEstimation += Math.max(getMinTabWidth(), myTabs.myInfo2Label.get(tabInfo).getPreferredSize().width);
    }

    final int extraWidth = data.toFitLength - lengthEstimation;
    float fractionalPart = 0;
    for (Iterator<TabInfo> iterator = data.toLayout.iterator(); iterator.hasNext(); ) {
      TabInfo tabInfo = iterator.next();
      final TabLabel label = myTabs.myInfo2Label.get(tabInfo);

      int length;
      int lengthIncrement = label.getPreferredSize().width;
      if (!iterator.hasNext()) {
        length = Math.min(data.toFitLength - spentLength, lengthIncrement);
      }
      else if (extraWidth <= 0 ) {//need compress
        float fLength = lengthIncrement * (float)data.toFitLength / lengthEstimation;
        fractionalPart += fLength - (int)fLength;
        length = (int)fLength;
        if (fractionalPart >= 1) {
          length++;
          fractionalPart -= 1;
        }
      }
      else {
        length = lengthIncrement;
      }
      if (tabInfo.isPinned()) {
        length = Math.min(getMaxPinnedTabWidth(), length);
      }
      spentLength += length + myTabs.getTabHGap();
      applyTabLayout(data, label, length);
      data.position = (int)label.getBounds().getMaxX() + myTabs.getTabHGap();
    }

    for (TabInfo eachInfo : data.toDrop) {
      JBTabsImpl.resetLayout(myTabs.myInfo2Label.get(eachInfo));
    }
  }

  @Override
  protected boolean applyTabLayout(SingleRowPassInfo data, TabLabel label, int length) {
    boolean result = super.applyTabLayout(data, label, length);
    label.setAlignmentToCenter(false);
    return result;
  }
}
