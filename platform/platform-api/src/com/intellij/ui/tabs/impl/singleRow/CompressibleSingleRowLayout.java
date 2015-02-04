/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.util.ui.GraphicsUtil;

import java.awt.*;
import java.util.Iterator;
import java.util.List;

public class CompressibleSingleRowLayout extends SingleRowLayout {
  public CompressibleSingleRowLayout(JBTabsImpl tabs) {
    super(tabs);
  }

  @Override
  public LayoutPassInfo layoutSingleRow(List<TabInfo> visibleInfos) {
    SingleRowPassInfo data = (SingleRowPassInfo)super.layoutSingleRow(visibleInfos);
    if (data.toLayout.size() > 0) {
      final TabLabel firstLabel = myTabs.myInfo2Label.get(data.toLayout.get(0));
      final TabLabel lastLabel = findLastVisibleLabel(data);
      if (firstLabel != null && lastLabel != null) {
        data.tabRectangle.x = firstLabel.getBounds().x;
        data.tabRectangle.y = firstLabel.getBounds().y;
        data.tabRectangle.width = data.requiredLength;
        data.tabRectangle.height = (int)lastLabel.getBounds().getMaxY() - data.tabRectangle.y;
      }
    }

    return data;
  }

  @Override
  protected void recomputeToLayout(SingleRowPassInfo data) {
    calculateRequiredLength(data);
    data.firstGhostVisible = false;
    data.lastGhostVisible = false;
  }

  @Override
  protected void layoutLabelsAndGhosts(SingleRowPassInfo data) {
    if (myTabs.getPresentation().getTabsPosition() != JBTabsPosition.top
        && myTabs.getPresentation().getTabsPosition() != JBTabsPosition.bottom) {
      super.layoutLabelsAndGhosts(data);
      return;
    }

    int tabWidth = 0;
    boolean layoutStopped = false;
    int lengthEstimation = 0;
    for (TabInfo eachInfo : data.toLayout) {
      final TabLabel label = myTabs.myInfo2Label.get(eachInfo);
      if (tabWidth == 0) {
        tabWidth = GraphicsUtil.stringWidth("m", label.getLabelComponent().getFont()) * 20;
      }
      lengthEstimation += tabWidth;
    }
    double compressionFactor = (double)lengthEstimation / data.toFitLength;

    int spentLength = 0;
    for (Iterator<TabInfo> iterator = data.toLayout.iterator(); iterator.hasNext(); ) {
      TabInfo eachInfo = iterator.next();
      final TabLabel label = myTabs.myInfo2Label.get(eachInfo);
      if (layoutStopped) {
        label.setActionPanelVisible(false);
        final Rectangle rec = getStrategy().getLayoutRect(data, 0, 0);
        myTabs.layout(label, rec);
        continue;
      }

      label.setActionPanelVisible(true);

      int length;
      if (compressionFactor > 1) {
        length = iterator.hasNext() ? (int)(tabWidth * (float)data.toFitLength / lengthEstimation)
                                    : data.toFitLength - spentLength - data.toLayout.size() / 2;
        spentLength += length;
      }
      else {
        length = tabWidth;
      }
      boolean continueLayout = applyTabLayout(data, label, length, 0);

      data.position = getStrategy().getMaxPosition(label.getBounds());
      data.position += myTabs.getInterTabSpaceLength();

      if (!continueLayout) {
        layoutStopped = true;
      }
    }

    for (TabInfo eachInfo : data.toDrop) {
      JBTabsImpl.resetLayout(myTabs.myInfo2Label.get(eachInfo));
    }
  }

  @Override
  protected boolean applyTabLayout(SingleRowPassInfo data, TabLabel label, int length, int deltaToFit) {
    boolean result = super.applyTabLayout(data, label, length, deltaToFit);
    label.setAlignmentToCenter(false);
    return result;
  }
}
