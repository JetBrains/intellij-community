package com.intellij.ui.tabs;

public interface TabsListener {

  void selectionChanged(TabInfo oldSelection, TabInfo newSelection);

  void beforeSelectionChanged(TabInfo oldSelection, TabInfo newSelection);
  
  class Adapter implements TabsListener {
    public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
    }

    public void beforeSelectionChanged(TabInfo oldSelection, TabInfo newSelection) {
    }
  }

}
