package com.intellij.ui.tabs.impl;

import com.intellij.ui.tabs.TabInfo;

import java.util.List;

public abstract class LayoutPassInfo {
  public abstract TabInfo getPreviousFor(TabInfo info);

  public abstract TabInfo getNextFor(TabInfo info);

  public TabInfo getPrevious(List<TabInfo> list, int i) {
    return i > 0 ? list.get(i - 1) : null;
  }

  public TabInfo getNext(List<TabInfo> list, int i) {
    return i < list.size() - 1 ? list.get(i + 1) : null;
  }

}
