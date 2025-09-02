// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl;

import com.intellij.ui.tabs.TabInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

@ApiStatus.Internal
public abstract class LayoutPassInfo {
  public final List<TabInfo> visibleInfos;

  public @NotNull Rectangle entryPointRect = new Rectangle();
  public @NotNull Rectangle moreRect = new Rectangle();
  public @NotNull Rectangle titleRect = new Rectangle();

  protected LayoutPassInfo(List<TabInfo> visibleInfos) {
    this.visibleInfos = visibleInfos;
  }

  public static @Nullable TabInfo getPrevious(List<TabInfo> list, int i) {
    return i > 0 ? list.get(i - 1) : null;
  }

  public static @Nullable TabInfo getNext(List<TabInfo> list, int i) {
    return i < list.size() - 1 ? list.get(i + 1) : null;
  }

  public abstract int getRowCount();

  public abstract @NotNull Rectangle getHeaderRectangle();

  public abstract int getRequiredLength();

  public abstract int getScrollExtent();
}
