package com.intellij.execution.ui.layout.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.content.Content;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.execution.ui.layout.RunnerLayoutUi;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class RunnerLayout {

  private String myID;

  protected Map<String, View> myViews = new HashMap<String, View>();
  private Map<String, View.Default> myDefaultViews = new HashMap<String, View.Default>();

  protected Set<Tab> myTabs = new TreeSet<Tab>(new Comparator<Tab>() {
    public int compare(final Tab o1, final Tab o2) {
      return o1.getIndex() - o2.getIndex();
    }
  });
  private Map<Integer, Tab.Default> myDefaultTabs = new HashMap<Integer, Tab.Default>();

  protected General myGeneral = new General();


  public RunnerLayout(final String ID) {
    myID = ID;
  }

  @NotNull
  public Tab getOrCreateTab(final int index) {
    Tab tab = findTab(index);
    if (tab != null) return tab;

    tab = createNewTab(index);

    return tab;
  }

  public Tab getDefaultTab() {
    return getOrCreateTab(0);
  }

  private Tab createNewTab(final int index) {
    final Tab tab;

    final Tab.Default defaultTab = getOrCreateDefaultTab(index);
    tab = defaultTab.createTab();

    myTabs.add(tab);

    return tab;
  }

  private Tab.Default getOrCreateDefaultTab(final int index) {
    Tab.Default tab = myDefaultTabs.get(index);
    if (tab == null) {
      tab = new Tab.Default(index, null, null);
      myDefaultTabs.put(index, tab);
    }
    return tab;
  }

  public Tab createNewTab() {
    int index = 0;
    for (Tab each : myTabs) {
      if (!isUsed(each)) return each;

      if (each.getIndex() < Integer.MAX_VALUE) {
        index = each.getIndex() + 1;
      }
      else {
        break;
      }
    }

    return createNewTab(index);
  }

  private boolean isUsed(Tab tab) {
    for (View each : myViews.values()) {
      if (each.getTab() == tab) return true;
    }

    return false;
  }

  @Nullable
  protected Tab findTab(int index) {
    for (Tab each : myTabs) {
      if (index == each.getIndex()) return each;
    }

    return null;
  }

  public Element getState() {
    return write(new Element("layout"));
  }

  public void loadState(final Element state) {
    read(state);
  }

  public Element read(final Element parentNode) {
    List tabs = parentNode.getChildren(StringUtil.getShortName(Tab.class.getName()));
    for (Object eachTabElement : tabs) {
      Tab eachTab = new Tab((Element)eachTabElement);
      getOrCreateTab(eachTab.getIndex()).read((Element)eachTabElement);
    }

    final List views = parentNode.getChildren(StringUtil.getShortName(View.class.getName()));
    for (Object content : views) {
      final View state = new View(this, (Element)content);
      myViews.put(state.getID(), state);
    }

    XmlSerializer.deserializeInto(myGeneral, parentNode.getChild(StringUtil.getShortName(myGeneral.getClass().getName(), '$')));

    return parentNode;
  }

  public Element write(final Element parentNode) {
    for (View eachState : myViews.values()) {
      eachState.write(parentNode);
    }

    for (Tab eachTab : myTabs) {
      eachTab.write(parentNode);
    }

    parentNode.addContent(XmlSerializer.serialize(myGeneral));

    return parentNode;
  }


  public void resetToDefault() {
    myViews.clear();
    myTabs.clear();
  }

  public boolean isToolbarHorizontal() {
    return myGeneral.horizontalToolbar;
  }

  public void setToolbarHorizontal(boolean horizontal) {
    myGeneral.horizontalToolbar = horizontal;
  }

  public View getStateFor(Content content) {
    return getOrCreateView(getOrCreateContentId(content));
  }

  private static String getOrCreateContentId(final Content content) {
    @NonNls String id = content.getUserData(View.ID);
    if (id == null) {
      id = "UnknownView-" + content.getDisplayName();
      content.putUserData(View.ID, id);
    }
    return id;
  }

  private View getOrCreateView(String id) {
    if (myViews.containsKey(id)) {
      return myViews.get(id);
    } else {
      final View.Default defaultView = getOrCreateDefault(id);
      final View view = defaultView.createView(this);
      myViews.put(id, view);
      return view;
    }
  }

  private View.Default getOrCreateDefault(String id) {
    if (myDefaultViews.containsKey(id)) {
      return myDefaultViews.get(id);
    } else {
      return setDefault(id, Integer.MAX_VALUE, RunnerLayoutUi.PlaceInGrid.bottom, false);
    }
  }


  public Tab.Default setDefault(int tabID, String displayName, Icon icon) {
    final Tab.Default tab = new Tab.Default(tabID, displayName, icon);
    myDefaultTabs.put(tabID, tab);
    return tab;
  }

  public View.Default setDefault(String id, int tabIndex, RunnerLayoutUi.PlaceInGrid placeInGrid, boolean isMinimized) {
    final View.Default view = new View.Default(id, tabIndex, placeInGrid, isMinimized);
    myDefaultViews.put(id, view);
    return view;
  }

  public RunnerLayoutUi.PlaceInGrid getDefaultGridPlace(final Content content) {
    return getOrCreateDefault(getOrCreateContentId(content)).getPlaceInGrid();
  }

  public int getDefaultSelectedTabIndex() {
    return 0;
  }

  public static class General {
    public volatile boolean horizontalToolbar = false;
    public volatile int selectedTab = 0;
  }
}
