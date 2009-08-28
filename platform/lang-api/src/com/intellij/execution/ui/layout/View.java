package com.intellij.execution.ui.layout;

public interface View {
  Tab getTab();

  PlaceInGrid getPlaceInGrid();

  boolean isMinimizedInGrid();

  int getTabIndex();

  void setMinimizedInGrid(boolean minimizedInGrid);

  void setPlaceInGrid(PlaceInGrid placeInGrid);

  void assignTab(Tab tab);

  void setTabIndex(int tabIndex);
}