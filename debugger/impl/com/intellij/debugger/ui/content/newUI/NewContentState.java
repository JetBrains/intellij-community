package com.intellij.debugger.ui.content.newUI;

public class NewContentState {

  private int myTab;
  private PlaceInGrid myPlaceInGrid;

  private float myGridSplitProportion;

  public NewContentState(int tab, final PlaceInGrid placeInGrid, float gridSplitProportion) {
    myTab = tab;
    myPlaceInGrid = placeInGrid;
    myGridSplitProportion = gridSplitProportion;
  }

  public int getTab() {
    return myTab;
  }

  public PlaceInGrid getPlaceInGrid() {
    return myPlaceInGrid;
  }

  public float getGridSplitProportion() {
    return myGridSplitProportion;
  }
}
