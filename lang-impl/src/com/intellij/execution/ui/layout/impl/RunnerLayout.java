package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ui.layout.RunnerLayoutUi;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.update.ComparableObjectCheck;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class RunnerLayout  {

  private String myID;

  protected Map<String, ViewImpl> myViews = new HashMap<String, ViewImpl>();
  private Map<String, ViewImpl.Default> myDefaultViews = new HashMap<String, ViewImpl.Default>();

  protected Set<TabImpl> myTabs = new TreeSet<TabImpl>(new Comparator<TabImpl>() {
    public int compare(final TabImpl o1, final TabImpl o2) {
      return o1.getIndex() - o2.getIndex();
    }
  });
  private Map<Integer, TabImpl.Default> myDefaultTabs = new HashMap<Integer, TabImpl.Default>();

  protected General myGeneral = new General();
  private String myDefaultToFocus;


  public RunnerLayout(final String ID) {
    myID = ID;
  }

  @NotNull
  public TabImpl getOrCreateTab(final int index) {
    TabImpl tab = findTab(index);
    if (tab != null) return tab;

    tab = createNewTab(index);

    return tab;
  }

  public TabImpl getDefaultTab() {
    return getOrCreateTab(0);
  }

  private TabImpl createNewTab(final int index) {
    final TabImpl tab;

    final TabImpl.Default defaultTab = getOrCreateDefaultTab(index);
    tab = defaultTab.createTab();

    myTabs.add(tab);

    return tab;
  }

  private TabImpl.Default getOrCreateDefaultTab(final int index) {
    TabImpl.Default tab = myDefaultTabs.get(index);
    if (tab == null) {
      tab = new TabImpl.Default(index, null, null);
      myDefaultTabs.put(index, tab);
    }
    return tab;
  }

  public TabImpl createNewTab() {
    int index = 0;
    for (TabImpl each : myTabs) {
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

  private boolean isUsed(TabImpl tab) {
    for (ViewImpl each : myViews.values()) {
      if (each.getTab() == tab) return true;
    }

    return false;
  }

  @Nullable
  protected TabImpl findTab(int index) {
    for (TabImpl each : myTabs) {
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
    List tabs = parentNode.getChildren(StringUtil.getShortName(TabImpl.class.getName()));
    for (Object eachTabElement : tabs) {
      TabImpl eachTab = new TabImpl((Element)eachTabElement);
      getOrCreateTab(eachTab.getIndex()).read((Element)eachTabElement);
    }

    final List views = parentNode.getChildren(StringUtil.getShortName(ViewImpl.class.getName()));
    for (Object content : views) {
      final ViewImpl state = new ViewImpl(this, (Element)content);
      myViews.put(state.getID(), state);
    }

    XmlSerializer.deserializeInto(myGeneral, parentNode.getChild(StringUtil.getShortName(myGeneral.getClass().getName(), '$')));

    return parentNode;
  }

  public Element write(final Element parentNode) {
    for (ViewImpl eachState : myViews.values()) {
      eachState.write(parentNode);
    }

    for (TabImpl eachTab : myTabs) {
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

  public ViewImpl getStateFor(Content content) {
    return getOrCreateView(getOrCreateContentId(content));
  }

  private static String getOrCreateContentId(final Content content) {
    @NonNls String id = content.getUserData(ViewImpl.ID);
    if (id == null) {
      id = "UnknownView-" + content.getDisplayName();
      content.putUserData(ViewImpl.ID, id);
    }
    return id;
  }

  private ViewImpl getOrCreateView(String id) {
    if (myViews.containsKey(id)) {
      return myViews.get(id);
    } else {
      final ViewImpl.Default defaultView = getOrCreateDefault(id);
      final ViewImpl view = defaultView.createView(this);
      myViews.put(id, view);
      return view;
    }
  }

  private ViewImpl.Default getOrCreateDefault(String id) {
    if (myDefaultViews.containsKey(id)) {
      return myDefaultViews.get(id);
    } else {
      return setDefault(id, Integer.MAX_VALUE, RunnerLayoutUi.PlaceInGrid.bottom, false);
    }
  }


  public TabImpl.Default setDefault(int tabID, String displayName, Icon icon) {
    final TabImpl.Default tab = new TabImpl.Default(tabID, displayName, icon);
    myDefaultTabs.put(tabID, tab);
    return tab;
  }

  public ViewImpl.Default setDefault(String id, int tabIndex, RunnerLayoutUi.PlaceInGrid placeInGrid, boolean isMinimized) {
    final ViewImpl.Default view = new ViewImpl.Default(id, tabIndex, placeInGrid, isMinimized);
    myDefaultViews.put(id, view);
    return view;
  }

  public RunnerLayoutUi.PlaceInGrid getDefaultGridPlace(final Content content) {
    return getOrCreateDefault(getOrCreateContentId(content)).getPlaceInGrid();
  }

  public int getDefaultSelectedTabIndex() {
    return 0;
  }

  public boolean isToFocusOnStartup(final String id) {
    return ComparableObjectCheck.equals(id, getToFocusOnStartup());
  }

  public void setToFocusOnStartup(final String id) {
    myGeneral.focusOnStart = id;
  }

  public void setDefaultToFocusOnstartup(String id) {
    myDefaultToFocus = id;
  }

  @Nullable
  public String getToFocusOnStartup() {
    return myGeneral.focusOnStart != null ? myGeneral.focusOnStart : myDefaultToFocus;
  }

  public static class General {
    public volatile boolean horizontalToolbar = false;
    public volatile int selectedTab = 0;
    public volatile String focusOnStart;
  }
}
