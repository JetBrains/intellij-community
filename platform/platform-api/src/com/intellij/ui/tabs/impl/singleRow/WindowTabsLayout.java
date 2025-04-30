// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public final class WindowTabsLayout extends SingleRowLayout {
  public WindowTabsLayout(JBTabsImpl tabs) {
    super(tabs);
  }

  @Override
  protected void recomputeToLayout(SingleRowPassInfo data) {
    data.requiredLength = tabs.getWidth();
    data.toLayout.addAll(data.visibleInfos);
  }

  @Override
  protected boolean applyTabLayout(SingleRowPassInfo data, TabLabel label, int length) {
    int tabCount = data.toLayout.size();

    if (tabCount > 0) {
      Rectangle bounds = getStrategy().getLayoutRect(data, data.position, length);

      int tabsWidth = tabs.getWidth();
      bounds.width = tabsWidth / tabCount;

      if (tabs.getIndexOf(label.getInfo()) == tabs.getTabCount() - 1) {
        int fullWidth = bounds.width * tabCount;
        if (fullWidth < tabsWidth) {
          bounds.width += tabsWidth - fullWidth;
        }
      }

      tabs.layout(label, bounds);
      label.setAlignmentToCenter(true);

      return true;
    }

    return super.applyTabLayout(data, label, length);
  }

  @Override
  public int getDropIndexFor(@NotNull Point point) {
    Component component = tabs.getComponentAt(point);
    if (component instanceof TabLabel label && lastSingRowLayout != null) {
      return lastSingRowLayout.visibleInfos.indexOf(label.getInfo());
    }
    return -1;
  }

  @Override
  public int getDropSideFor(@NotNull Point point) {
    return -1;
  }

  @Override
  public boolean isDragOut(@NotNull TabLabel tabLabel, int deltaX, int deltaY) {
    Rectangle bounds = tabLabel.getBounds();
    if (bounds.x + bounds.width + deltaX < 0 || bounds.x + bounds.width > tabLabel.getParent().getWidth()) return true;
    return Math.abs(deltaY) > tabLabel.getHeight();
  }
}