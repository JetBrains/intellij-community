package com.intellij.execution.ui.layout;

public interface View {
  Tab getTab();

  RunnerLayoutUi.PlaceInGrid getPlaceInGrid();

  boolean isMinimizedInGrid();

  int getTabIndex();

  void setMinimizedInGrid(boolean minimizedInGrid);

  void setPlaceInGrid(RunnerLayoutUi.PlaceInGrid placeInGrid);

  void assignTab(Tab tab);

  void setTabIndex(int tabIndex);
}