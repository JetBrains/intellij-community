package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.settings.DebuggerLayoutSettings;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class NewContentState {

  private static final String ID = "id";
  private static final String TAB = "tab";
  private static final String PLACE_IN_GRID = "placeInGrid";
  private static final String IS_MINIMIZED = "isMinimized";
  private static final String IS_DETACHED = "isDetached";
  private static final String X = "x";
  private static final String Y = "y";

  private String myID;

  private Tab myTab;
  private PlaceInGrid myPlaceInGrid;

  private boolean myMinimizedInGrid;

  private boolean myDetached;
  private Point myDetachPoint;

  public NewContentState(String id, Tab tab, final PlaceInGrid placeInGrid, boolean minimizedInGrid) {
    myID = id;
    myTab = tab;
    myPlaceInGrid = placeInGrid;
    myMinimizedInGrid = minimizedInGrid;
  }

  public NewContentState(DebuggerLayoutSettings settings, Element element) {
    myID = element.getAttributeValue(ID);
    myTab = settings.getOrCreateTab(Integer.valueOf(element.getAttributeValue(TAB)).intValue());
    myPlaceInGrid = PlaceInGrid.valueOf(element.getAttributeValue(PLACE_IN_GRID));
    myMinimizedInGrid = Boolean.valueOf(element.getAttributeValue(IS_MINIMIZED)).booleanValue();
    myDetached = Boolean.valueOf(element.getAttributeValue(IS_DETACHED)).booleanValue();

    String x = element.getAttributeValue(X);
    String y = element.getAttributeValue(Y);
    if (x != null && y != null) {
      myDetachPoint = new Point(Integer.valueOf(x).intValue(), Integer.valueOf(y).intValue());
    }
  }


  public void write(final Element content) {
    content.setAttribute(ID, myID);
    content.setAttribute(TAB, String.valueOf(myTab.getIndex()));
    content.setAttribute(PLACE_IN_GRID, myPlaceInGrid.name());
    content.setAttribute(IS_MINIMIZED, String.valueOf(myMinimizedInGrid));
    content.setAttribute(IS_DETACHED, String.valueOf(myDetached));
    if (myDetachPoint != null) {
      content.setAttribute(X, String.valueOf(myDetachPoint.x));
      content.setAttribute(Y, String.valueOf(myDetachPoint.y));
    }
  }

  public Tab getTab() {
    return myTab;
  }

  public PlaceInGrid getPlaceInGrid() {
    return myPlaceInGrid;
  }


  public boolean isMinimizedInGrid() {
    return myMinimizedInGrid;
  }


  public String getID() {
    return myID;
  }

  public boolean isDetached() {
    return myDetached;
  }

  @Nullable
  public Point getDetachPoint() {
    return myDetachPoint;
  }

  public void setMinimizedInGrid(final boolean minimizedInGrid) {
    myMinimizedInGrid = minimizedInGrid;
  }

  public void setPlaceInGrid(PlaceInGrid placeInGrid) {
    myPlaceInGrid = placeInGrid;
  }

  public void setTab(final Tab tab) {
    myTab = tab;
  }
}
