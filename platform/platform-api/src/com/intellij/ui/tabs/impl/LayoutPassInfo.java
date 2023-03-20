// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl;

import com.intellij.ui.tabs.TabInfo;
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

  public abstract int getRowCount();

  public abstract Rectangle getHeaderRectangle();

  public abstract int getRequiredLength();
}
