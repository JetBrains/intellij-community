package com.intellij.execution.ui.layout;

public interface Tab {

  boolean isDefault();
  void setDetached(final PlaceInGrid placeInGrid, final boolean detached);

  int getIndex();

  boolean isDetached(final PlaceInGrid placeInGrid);
}