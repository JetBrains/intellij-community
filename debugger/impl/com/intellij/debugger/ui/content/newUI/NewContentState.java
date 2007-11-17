package com.intellij.debugger.ui.content.newUI;

public class NewContentState {

  private ContentContainer.Type myContainer;
  private PlaceInGrid myPlaceInGrid;

  private float myGridSplitProportion;

  public NewContentState(ContentContainer.Type container, final PlaceInGrid placeInGrid, float gridSplitProportion) {
    myContainer = container;
    myPlaceInGrid = placeInGrid;
    myGridSplitProportion = gridSplitProportion;
  }

  public ContentContainer.Type getContainer() {
    return myContainer;
  }

  public PlaceInGrid getPlaceInGrid() {
    return myPlaceInGrid;
  }

  public float getGridSplitProportion() {
    return myGridSplitProportion;
  }
}
