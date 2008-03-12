package com.intellij.ui.tabs;

import com.intellij.ui.tabs.impl.TabInfo;

public interface TabsListener {

  void selectionChanged(TabInfo oldSelection, TabInfo newSelection);

}
