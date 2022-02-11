// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl;

import com.intellij.ui.tabs.TabInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public abstract class LayoutPassInfo {

  public final List<TabInfo> myVisibleInfos;

  protected LayoutPassInfo(List<TabInfo> visibleInfos) {
    myVisibleInfos = visibleInfos;
  }

  @Nullable
  public static TabInfo getPrevious(List<TabInfo> list, int i) {
    return i > 0 ? list.get(i - 1) : null;
  }

  @Nullable
  public static TabInfo getNext(List<TabInfo> list, int i) {
    return i < list.size() - 1 ? list.get(i + 1) : null;
  }

  /**
   * @deprecated Will be removed in close future. If you suddenly need this,
   * then your code probably should be realized inside the {@link TabsLayout} implementation.
   */
  @Deprecated(forRemoval = true)
  public abstract int getRowCount();

  public abstract Rectangle getHeaderRectangle();

  @ApiStatus.Experimental
  public List<LineCoordinates> getExtraBorderLines() {
    return null;
  }

  @ApiStatus.Experimental
  public static class LineCoordinates {
    public int x1;
    public int y1;
    public int x2;
    public int y2;

    public LineCoordinates(int x1, int y1, int x2, int y2) {
      this.x1 = x1;
      this.y1 = y1;
      this.x2 = x2;
      this.y2 = y2;
    }

    public Point from() {
      return new Point(x1, y1);
    }

    public Point to() {
      return new Point(x2, y2);
    }

    @Override
    public boolean equals(Object another) {
      if (another instanceof LineCoordinates) {
        LineCoordinates anotherLine = (LineCoordinates)another;
        return this.x1 == anotherLine.x1 && this.y1 == anotherLine.y1 &&
               this.x2 == anotherLine.x2 && this.y2 == anotherLine.y2;
      }
      return false;
    }
  }
}
