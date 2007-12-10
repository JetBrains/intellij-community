package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.settings.DebuggerLayoutSettings;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;

public class View {

  private String myID;

  private Tab myTab;
  private int myTabIndex;

  private PlaceInGrid myPlaceInGrid;

  private boolean myMinimizedInGrid;

  public View(String id, Tab tab, final PlaceInGrid placeInGrid, boolean minimizedInGrid) {
    myID = id;
    myTab = tab;
    myPlaceInGrid = placeInGrid;
    myMinimizedInGrid = minimizedInGrid;
  }

  public void setID(final String ID) {
    myID = ID;
  }

  public View(DebuggerLayoutSettings settings, Element element) {
    XmlSerializer.deserializeInto(this, element);
    assignTab(settings.getOrCreateTab(myTabIndex));
  }

  public void write(final Element content) {
    content.addContent(XmlSerializer.serialize(this));
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


  public void setMinimizedInGrid(final boolean minimizedInGrid) {
    myMinimizedInGrid = minimizedInGrid;
  }

  public void setPlaceInGrid(PlaceInGrid placeInGrid) {
    myPlaceInGrid = placeInGrid;
  }

  public void assignTab(final Tab tab) {
    myTab = tab;
  }

  public int getTabIndex() {
    return myTab != null ? myTab.getIndex() : myTabIndex;
  }

  public void setTabIndex(final int tabIndex) {
    myTabIndex = tabIndex;
  }
}
