package com.intellij.execution.ui.layout;

public interface Tab {

  boolean isDefault();
  void setDetached(final RunnerLayoutUi.PlaceInGrid placeInGrid, final boolean detached);

  int getIndex();

  boolean isDetached(final RunnerLayoutUi.PlaceInGrid placeInGrid);
}